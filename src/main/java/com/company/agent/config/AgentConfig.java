package com.company.agent.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p><b>全局 Bean 配置与多 Agent 专用并发线程池</b></p>
 * <p>说明：提供同步/流式/路由三个模型单例，并构造带背压与有界队列的 ThreadPoolExecutor，防止阻塞主线程。</p>
 * <p>注意：存在多个 ChatLanguageModel Bean，注入时必须用 @Qualifier 指定名称。</p>
 *
 * @author Enterprise Agent Platform Team
 * @version 2.0.0
 */
@Configuration
@RequiredArgsConstructor
public class AgentConfig {

    private final AgentProperties agentProperties;

    /** 流式模型：领域 Agent 的 TokenStream 使用 */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(agentProperties.getApiKey())
                .baseUrl(agentProperties.getBaseUrl())
                .modelName(agentProperties.getModelName())
                .temperature(agentProperties.getTemperature())
                .timeout(Duration.ofSeconds(agentProperties.getTimeoutSeconds()))
                .logRequests(agentProperties.getLogRequests())
                .logResponses(agentProperties.getLogResponses())
                .build();
    }

    /** 同步模型：领域 Agent 的同步调用使用 */
    @Bean(name = "chatLanguageModel")
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(agentProperties.getApiKey())
                .baseUrl(agentProperties.getBaseUrl())
                .modelName(agentProperties.getModelName())
                .temperature(agentProperties.getTemperature())
                .timeout(Duration.ofSeconds(agentProperties.getTimeoutSeconds()))
                .logRequests(agentProperties.getLogRequests())
                .logResponses(agentProperties.getLogResponses())
                .build();
    }

    /** 路由模型：只输出几个字的领域代号，温度 0、限制输出长度；可配置为更小更便宜的模型 */
    @Bean(name = "routerChatModel")
    public ChatLanguageModel routerChatModel() {
        String routerModel = (agentProperties.getRouterModelName() == null
                || agentProperties.getRouterModelName().isBlank())
                ? agentProperties.getModelName()
                : agentProperties.getRouterModelName();
        return OpenAiChatModel.builder()
                .apiKey(agentProperties.getApiKey())
                .baseUrl(agentProperties.getBaseUrl())
                .modelName(routerModel)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(agentProperties.getTimeoutSeconds()))
                .logRequests(agentProperties.getLogRequests())
                .logResponses(agentProperties.getLogResponses())
                .build();
    }

    /**
     * 专属异步多 Agent 调度线程池，拒绝策略采用 CallerRunsPolicy 实现天然背压
     */
    @Bean(name = "agentExecutorPool")
    public ExecutorService agentExecutorPool() {
        return new ThreadPoolExecutor(
                16, 32, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadFactory() {
                    private final AtomicInteger count = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "agent-exec-" + count.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
