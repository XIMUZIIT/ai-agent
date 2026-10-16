package com.company.agent.tool;

import com.company.agent.semantic.MetricQueryService;
import com.company.agent.semantic.MetricRegistry;
import com.company.agent.semantic.SemanticMetricTools;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * <p><b>领域工具工厂：为每个领域创建"只能访问本领域视图"的通用工具</b></p>
 * <p>通用工具（指标查询、受限 SQL）不再是全局单例，而是按领域各建一份，
 * 视图白名单在创建时就固定下来，领域之间互相隔离。</p>
 */
@Component
@RequiredArgsConstructor
public class DomainToolFactory {

    private final JdbcTemplate agentJdbcTemplate;
    private final MetricRegistry metricRegistry;
    private final MetricQueryService metricQueryService;

    /** 第 3 层：受限 Text-to-SQL，只能查询指定视图 */
    public SqlQueryTool sqlTool(Set<String> views) {
        return new SqlQueryTool(agentJdbcTemplate, views);
    }

    /** 第 1 层：语义指标查询，只暴露数据来源在指定视图内的指标 */
    public SemanticMetricTools metricTools(Set<String> views) {
        return new SemanticMetricTools(metricRegistry, metricQueryService, views);
    }
}
