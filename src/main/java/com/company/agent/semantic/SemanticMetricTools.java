package com.company.agent.semantic;

import com.alibaba.fastjson2.JSON;
import com.company.agent.semantic.SemanticProperties.MetricDef;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * <p><b>第 1 层工具：按指标查询</b></p>
 * <p>不是 Spring Bean：由 DomainToolFactory 按领域创建，只暴露数据来源在 views 内的指标。
 * 只做参数透传和结果格式化，口径与 SQL 拼装在语义层（MetricRegistry / MetricQueryService）。</p>
 */
public class SemanticMetricTools {

    private final MetricRegistry registry;
    private final MetricQueryService queryService;
    private final Set<String> views;

    public SemanticMetricTools(MetricRegistry registry, MetricQueryService queryService, Set<String> views) {
        this.registry = registry;
        this.queryService = queryService;
        this.views = views;
    }

    @Tool("列出当前领域所有可查询的业务指标、可用维度和口径说明。不确定有哪些指标时先调用它")
    public String listMetrics() {
        String catalog = registry.catalog(views);
        return catalog.isBlank() ? "当前领域没有可用指标" : catalog;
    }

    @Tool("按统一业务口径查询指标，可按维度分组。例如：近7天按渠道统计销售额、本月每天的有效订单数")
    public String queryMetric(
            @P("指标名，必须来自 listMetrics，如 sales_amount") String metric,
            @P("分组维度，必须来自该指标的可用维度；不需要分组传空字符串") String dimension,
            @P("起始日期，格式 yyyy-MM-dd（含）；无时间范围的指标传空字符串") String startDate,
            @P("结束日期，格式 yyyy-MM-dd（含）；无时间范围的指标传空字符串") String endDate) {

        Optional<MetricDef> def = registry.getMetric(metric);
        if (def.isEmpty() || !views.contains(def.get().getView())) {
            return "错误：当前领域不存在指标 " + metric + "，请先调用 listMetrics 查看可用指标";
        }
        try {
            List<Map<String, Object>> rows = queryService.query(metric, dimension, startDate, endDate);
            return rows.isEmpty() ? "没有查询到数据" : JSON.toJSONString(rows);
        } catch (IllegalArgumentException e) {
            return "错误：" + e.getMessage();
        }
    }
}
