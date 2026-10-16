package com.company.agent.semantic;

import com.company.agent.semantic.SemanticProperties.MetricDef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p><b>语义层查询服务：根据指标定义拼装 SQL 并执行</b></p>
 * <p>所有拼接进 SQL 的片段（视图、表达式、过滤条件、维度）都来自配置白名单；
 * 用户/模型给的日期走参数绑定。参数不合法抛 IllegalArgumentException，由工具转成错误提示回给模型。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricQueryService {

    private final MetricRegistry registry;
    private final JdbcTemplate agentJdbcTemplate;

    public List<Map<String, Object>> query(String metric, String dimension, String startDate, String endDate) {
        MetricDef m = registry.getMetric(metric).orElseThrow(() ->
                new IllegalArgumentException("不存在指标 " + metric + "，请先调用 listMetrics 查看可用指标"));

        String dimExpr = null;
        boolean timeDimension = false;
        if (dimension != null && !dimension.isBlank()) {
            dimExpr = registry.dimensionExpr(m.getView(), dimension);
            if (dimExpr == null) {
                throw new IllegalArgumentException("指标 " + metric + " 不支持维度 " + dimension
                        + "，可用维度：" + registry.dimensionNames(m.getView()));
            }
            timeDimension = "day".equals(dimension) || "month".equals(dimension);
        }

        StringBuilder sql = new StringBuilder("SELECT ")
                .append(dimExpr == null ? "'ALL'" : dimExpr).append(" AS dim, ")
                .append(m.getExpr()).append(" AS metric_value FROM ").append(m.getView())
                .append(" WHERE ").append(m.getFilter());

        List<Object> args = new ArrayList<>();
        if (m.getTimeCol() != null) {
            try {
                LocalDate start = LocalDate.parse(startDate);
                LocalDate end = LocalDate.parse(endDate);
                if (end.isBefore(start)) {
                    throw new IllegalArgumentException("结束日期早于起始日期");
                }
                sql.append(" AND ").append(m.getTimeCol()).append(" >= ? AND ")
                        .append(m.getTimeCol()).append(" < ?");
                args.add(start.atStartOfDay());
                args.add(end.plusDays(1).atStartOfDay());
            } catch (DateTimeParseException | NullPointerException e) {
                throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd");
            }
        }

        if (dimExpr != null) {
            sql.append(" GROUP BY dim");
            // 时间维度按时间升序展示趋势；其他维度按指标值降序
            sql.append(timeDimension ? " ORDER BY dim ASC LIMIT 400" : " ORDER BY metric_value DESC LIMIT 50");
        }

        log.info("[Metric] {} dim={} {}~{}", metric, dimension, startDate, endDate);
        List<Map<String, Object>> rows = agentJdbcTemplate.queryForList(sql.toString(), args.toArray());
        rows.removeIf(r -> r.get("metric_value") == null);
        return rows;
    }
}
