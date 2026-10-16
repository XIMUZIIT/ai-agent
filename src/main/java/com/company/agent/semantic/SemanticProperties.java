package com.company.agent.semantic;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p><b>语义层配置（agent.semantic 前缀）</b></p>
 * <p>指标口径和维度定义放在配置里，业务方可在 Nacos 中维护，新增指标不用改代码、不用发版。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.semantic")
public class SemanticProperties {

    /** 指标列表 */
    private List<MetricDef> metrics = new ArrayList<>();

    /** 维度定义：视图名 -> (维度名 -> SQL 表达式) */
    private Map<String, Map<String, String>> dimensions = new LinkedHashMap<>();

    @Data
    public static class MetricDef {
        /** 指标代号，如 sales_amount */
        private String name;
        /** 口径说明，会展示给模型 */
        private String desc;
        /** 数据来源视图 */
        private String view;
        /** 聚合表达式，如 SUM(pay_amount) */
        private String expr;
        /** 口径过滤条件，如 status IN ('PAID','COMPLETED') */
        private String filter = "1=1";
        /** 时间列；为空表示当前快照类指标（如库存） */
        private String timeCol;
    }
}
