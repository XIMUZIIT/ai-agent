package com.company.agent.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <p><b>订单查询仓储（第 2 层固定查询的 SQL 集中在这里）</b></p>
 * <p>只查询 AI 视图 v_order_ai。"有效订单"口径与 application.yml 中的语义层保持一致。</p>
 */
@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {

    private final JdbcTemplate agentJdbcTemplate;

    /** 最近一段时间内，有效订单（已支付/已完成）中实付金额最大的订单 */
    public List<Map<String, Object>> findLargestOrders(LocalDateTime since, int limit) {
        String sql = """
                SELECT order_no, customer_name, product_category, channel,
                       pay_amount, status, create_time
                FROM v_order_ai
                WHERE status IN ('PAID','COMPLETED')
                  AND create_time >= ?
                ORDER BY pay_amount DESC
                LIMIT ?
                """;
        return agentJdbcTemplate.queryForList(sql, since, limit);
    }
}
