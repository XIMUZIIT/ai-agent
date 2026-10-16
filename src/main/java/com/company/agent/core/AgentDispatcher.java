package com.company.agent.core;

import com.company.agent.core.AgentRegistry.DomainRuntime;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p><b>统一入口调度器：先路由，再分发给对应领域 Agent</b></p>
 * <ul>
 *   <li>路由时携带该会话最近几轮的“领域 + 问题”，用于处理“那明天的呢”这类追问；</li>
 *   <li>一个问题可路由到多个领域（复合问题），各领域分别回答后合并；</li>
 *   <li>路由模型失败、或输出无法使用时，用各领域声明的关键词兜底，不让用户干等或看到误导性的“没听懂”；</li>
 *   <li>路由服务本身故障与“用户问题不明确”分开提示；</li>
 *   <li>other / clarify / route_failed 直接返回固定话术，不进入任何领域 Agent。</li>
 * </ul>
 */
@Slf4j
@Component
public class AgentDispatcher {

    private static final int HISTORY_SIZE = 3;
    private static final int QUESTION_PREVIEW = 80;
    private static final Pattern TOKEN = Pattern.compile("[a-z_]+");
    private static final String DOMAIN_FAILED = "该领域查询失败，请稍后重试";
    /** 路由服务本身出错的内部标记（区别于“用户问题不明确”的 clarify），不写入历史记录 */
    private static final String ROUTE_FAILED = "route_failed";

    /** 一轮对话：路由到的领域（other/clarify 时为空）与用户问题 */
    private record Turn(List<String> domains, String question) {}

    private final AgentRegistry registry;
    private final ExecutorService executor;
    /** 领域代号 -> 兜底关键词（小写） */
    private final Map<String, List<String>> keywords = new LinkedHashMap<>();

    /** 每个会话最近几轮的路由记录，2 小时无访问过期 */
    private final Cache<String, List<Turn>> history = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(2, TimeUnit.HOURS)
            .build();

    public AgentDispatcher(AgentRegistry registry,
            List<DomainModule> modules,
            @Qualifier("agentExecutorPool") ExecutorService executor) {
        this.registry = registry;
        this.executor = executor;
        for (DomainModule m : modules) {
            List<String> words = m.keywords().stream()
                    .filter(w -> w != null && !w.isBlank())
                    .map(w -> w.trim().toLowerCase(Locale.ROOT))
                    .toList();
            keywords.put(m.name(), words);
            if (words.isEmpty()) {
                log.warn("[Router] 领域 {} 没有配置兜底关键词，路由模型异常时该领域无法被关键词命中", m.name());
            }
        }
    }

    // ==================== 路由 ====================

    /**
     * 路由：返回一个或多个领域代号，或单个 other / clarify / route_failed（阻塞调用）。
     * <p>优先级：路由模型结果 &gt; 关键词兜底 &gt;（仅异常时）沿用上一轮领域 &gt; 提示用户。</p>
     */
    public List<String> route(String sessionId, String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            return List.of(AgentRegistry.CLARIFY);
        }

        List<Turn> turns = history.getIfPresent(sessionId);
        List<String> result;
        String source;

        try {
            String raw = registry.router().classify(registry.routerRules(), buildRouterInput(turns, q));
            List<String> parsed = parse(raw);   // null 表示输出为空、混杂或无法识别
            log.info("[Router] session={} question=[{}] raw=[{}] parsed={}", sessionId, q, raw, parsed);

            if (parsed == null || isClarify(parsed)) {
                // 模型没给出可用结果：用关键词兜底，比直接反问用户体验更好
                List<String> byKeyword = matchKeywords(q);
                if (!byKeyword.isEmpty()) {
                    result = byKeyword;
                    source = "关键词兜底(模型输出不可用)";
                } else {
                    result = parsed == null ? List.of(AgentRegistry.CLARIFY) : parsed;
                    source = parsed == null ? "模型输出无法解析" : "模型";
                }
            } else {
                result = parsed;
                source = "模型";
            }
        } catch (Exception e) {
            // 打完整堆栈：用户看到的只是一句话术，真正的原因（鉴权、模型名、超时、参数不支持等）必须在日志里可见
            log.error("[Router] 路由模型调用失败 session={} question=[{}]: {}", sessionId, q, e.getMessage(), e);
            List<String> byKeyword = matchKeywords(q);
            List<String> last = lastDomains(turns);
            if (!byKeyword.isEmpty()) {
                result = byKeyword;
                source = "关键词兜底(路由模型异常)";
            } else if (!last.isEmpty()) {
                result = last;
                source = "沿用上一轮领域(路由模型异常)";
            } else {
                result = List.of(ROUTE_FAILED);
                source = "路由失败";
            }
        }

        log.info("[Router] session={} 最终路由={} 来源={}", sessionId, result, source);
        if (!ROUTE_FAILED.equals(result.get(0))) {
            record(sessionId, q, result);
        }
        return result;
    }

    private String buildRouterInput(List<Turn> turns, String question) {
        String recent = (turns == null || turns.isEmpty())
                ? "无"
                : turns.stream()
                        .map(t -> "[" + (t.domains().isEmpty() ? "无领域" : String.join("+", t.domains())) + "] "
                                + t.question())
                        .collect(Collectors.joining("\n"));
        return "最近对话:\n" + recent + "\n当前问题: " + question;
    }

    /**
     * 从模型输出中提取领域代号。
     * <ul>
     *   <li>只含领域代号 → 返回这些领域（最多 MAX_DOMAINS 个）；</li>
     *   <li>只含 other → [other]；只含 clarify → [clarify]；</li>
     *   <li>为空、领域与 other/clarify 混杂（如模型写了一句解释）、或什么都没识别到 → 返回 null，交给关键词兜底。</li>
     * </ul>
     */
    private List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        boolean sawOther = false;
        boolean sawClarify = false;

        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            String t = m.group();
            if (registry.has(t)) {
                if (domains.size() < AgentRegistry.MAX_DOMAINS) {
                    domains.add(t);
                }
            } else if (AgentRegistry.OTHER.equals(t)) {
                sawOther = true;
            } else if (AgentRegistry.CLARIFY.equals(t)) {
                sawClarify = true;
            }
        }

        // 去掉所有英文单词和常见分隔符后若还有内容，说明模型没有只输出代号（写了解释或中文）
        boolean clean = text.replaceAll("[a-z_]+", "")
                .replaceAll("[\\s,，、;；+`'\"*.。]", "").isEmpty();
        if (!clean) {
            log.warn("[Router] 模型输出包含代号以外的内容，建议检查路由提示词或模型: [{}]", raw);
        }

        if (!domains.isEmpty() && !sawOther && !sawClarify) {
            return List.copyOf(domains);
        }
        if (domains.isEmpty() && sawOther && !sawClarify) {
            return List.of(AgentRegistry.OTHER);
        }
        if (domains.isEmpty() && sawClarify && !sawOther) {
            return List.of(AgentRegistry.CLARIFY);
        }
        return null;
    }

    /** 关键词兜底：问题里包含某领域的任一关键词就命中该领域，可同时命中多个（复合问题） */
    private List<String> matchKeywords(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        List<String> hit = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : keywords.entrySet()) {
            if (!registry.has(e.getKey())) {
                continue;
            }
            boolean matched = e.getValue().stream().anyMatch(q::contains);
            if (matched) {
                hit.add(e.getKey());
                if (hit.size() >= AgentRegistry.MAX_DOMAINS) {
                    break;
                }
            }
        }
        return hit;
    }

    private boolean isClarify(List<String> domains) {
        return domains.size() == 1 && AgentRegistry.CLARIFY.equals(domains.get(0));
    }

    private List<String> lastDomains(List<Turn> turns) {
        if (turns == null) {
            return List.of();
        }
        for (int i = turns.size() - 1; i >= 0; i--) {
            if (!turns.get(i).domains().isEmpty()) {
                return turns.get(i).domains();
            }
        }
        return List.of();
    }

    private void record(String sessionId, String question, List<String> result) {
        List<String> domains = result.stream().filter(registry::has).toList();
        String preview = question.length() > QUESTION_PREVIEW ? question.substring(0, QUESTION_PREVIEW) + "…" : question;
        history.asMap().compute(sessionId, (k, old) -> {
            List<Turn> next = old == null ? new ArrayList<>() : new ArrayList<>(old);
            next.add(new Turn(domains, preview));
            while (next.size() > HISTORY_SIZE) {
                next.remove(0);
            }
            return next;
        });
    }

    /** other / clarify / route_failed 的固定话术；返回 null 表示需要进入领域 Agent */
    private String fixedReply(List<String> domains) {
        if (domains.size() != 1) {
            return null;
        }
        String d = domains.get(0);
        if (AgentRegistry.OTHER.equals(d)) {
            return "我是业务数据助手，可以帮您查询：" + registry.capabilities() + "。请问需要查什么？";
        }
        if (AgentRegistry.CLARIFY.equals(d)) {
            return "没太明白您想查询哪方面，请补充说明。我可以帮您查询：" + registry.capabilities() + "。";
        }
        if (ROUTE_FAILED.equals(d)) {
            return "系统暂时无法判断您的问题属于哪个业务，这不是您的问题，请稍后重试；如果持续出现，请联系管理员。";
        }
        return null;
    }

    private String titleOf(String domain) {
        return registry.get(domain).module().title();
    }

    // ==================== 同步调用 ====================

    /** 同步调用（阻塞，调用方需放在弹性线程池执行） */
    public String chat(String sessionId, String question) {
        List<String> domains = route(sessionId, question);
        String fixed = fixedReply(domains);
        if (fixed != null) {
            return fixed;
        }
        if (domains.size() == 1) {
            return runSync(domains.get(0), sessionId, question);
        }

        // 复合问题：各领域并行回答，按路由顺序合并
        List<CompletableFuture<String>> futures = domains.stream()
                .map(d -> CompletableFuture.supplyAsync(() -> runSync(d, sessionId, question), executor)
                        .exceptionally(e -> {
                            log.error("[Agent] 领域 {} 执行失败", d, e);
                            return DOMAIN_FAILED;
                        }))
                .toList();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < domains.size(); i++) {
            sb.append("【").append(titleOf(domains.get(i))).append("】\n")
                    .append(futures.get(i).join()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String runSync(String domain, String sessionId, String question) {
        DomainRuntime rt = registry.get(domain);
        return rt.sync().chat(sessionId, rt.systemPrompt(), question);
    }

    // ==================== 流式调用 ====================

    public Flux<String> stream(String sessionId, String question) {
        return Mono.fromCallable(() -> route(sessionId, question))
                .subscribeOn(Schedulers.boundedElastic())   // 路由是阻塞调用，不能占用 Netty 线程
                .flatMapMany(domains -> {
                    String fixed = fixedReply(domains);
                    if (fixed != null) {
                        return Flux.just(fixed);
                    }
                    if (domains.size() == 1) {
                        return streamDomain(domains.get(0), sessionId, question);
                    }
                    // 复合问题：按顺序依次输出各领域的回答，每段前加小标题
                    return Flux.fromIterable(domains).concatMap(d -> Flux.concat(
                            Flux.just("\n【" + titleOf(d) + "】\n"),
                            streamDomain(d, sessionId, question)));
                });
    }

    private Flux<String> streamDomain(String domain, String sessionId, String question) {
        DomainRuntime rt = registry.get(domain);
        return Flux.<String>create(emitter -> {
                    TokenStream ts = rt.stream().chatStream(sessionId, rt.systemPrompt(), question);
                    ts.onNext(emitter::next)
                            .onComplete(response -> emitter.complete())
                            .onError(emitter::error)
                            .start();
                })
                .onErrorResume(e -> {
                    log.error("[Agent] 领域 {} 流式执行失败", domain, e);
                    return Flux.just(DOMAIN_FAILED);
                });
    }
}
