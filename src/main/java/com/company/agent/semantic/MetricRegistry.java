package com.company.agent.semantic;

import com.company.agent.semantic.SemanticProperties.MetricDef;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <p><b>第 1 层：语义层 / 指标层</b></p>
 * <p>指标与维度来自配置（SemanticProperties），每次调用时实时读取，Nacos 配置刷新后自动生效。
 * 模型只能"选指标、选维度"，SQL 由 MetricQueryService 拼装，表名、过滤条件都来自配置，模型无法改动。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricRegistry {

    private static final Pattern VIEW_NAME = Pattern.compile("^v_[a-z0-9_]+$");

    private final SemanticProperties props;

    /** 启动时校验配置，发现问题直接让应用启动失败，而不是运行时才暴露 */
    @PostConstruct
    void validate() {
        for (MetricDef m : props.getMetrics()) {
            if (m.getName() == null || m.getExpr() == null || m.getView() == null
                    || !VIEW_NAME.matcher(m.getView()).matches()) {
                throw new IllegalStateException("语义层指标配置不合法（view 必须以 v_ 开头）: " + m.getName());
            }
            for (String s : new String[]{m.getExpr(), m.getFilter(), m.getTimeCol()}) {
                if (s != null && (s.contains(";") || s.contains("--"))) {
                    throw new IllegalStateException("语义层指标配置包含非法字符: " + m.getName());
                }
            }
        }
        log.info("[Semantic] 已加载 {} 个指标", props.getMetrics().size());
    }

    public Optional<MetricDef> getMetric(String name) {
        return props.getMetrics().stream().filter(m -> m.getName().equals(name)).findFirst();
    }

    public String dimensionExpr(String view, String dimension) {
        return props.getDimensions().getOrDefault(view, Map.of()).get(dimension);
    }

    public Set<String> dimensionNames(String view) {
        return props.getDimensions().getOrDefault(view, Map.of()).keySet();
    }

    /** 给模型看的指标目录：只包含数据来源在 views 内的指标（领域隔离） */
    public String catalog(Set<String> views) {
        StringBuilder sb = new StringBuilder();
        for (MetricDef m : props.getMetrics()) {
            if (!views.contains(m.getView())) {
                continue;
            }
            sb.append("- ").append(m.getName()).append(": ").append(m.getDesc())
                    .append("；可用维度: ").append(dimensionNames(m.getView()))
                    .append(m.getTimeCol() == null ? "；无时间范围（当前快照）" : "；需要起止日期")
                    .append("\n");
        }
        return sb.toString();
    }
}
