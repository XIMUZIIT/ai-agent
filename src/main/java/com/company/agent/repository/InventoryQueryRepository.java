package com.company.agent.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** 库存查询仓储：只查询 AI 视图 v_inventory_ai */
@Repository
@RequiredArgsConstructor
public class InventoryQueryRepository {

    private final JdbcTemplate agentJdbcTemplate;

    /** 按商品名称关键字模糊查询库存 */
    public List<Map<String, Object>> findByProductName(String keyword, int limit) {
        String sql = """
                SELECT product_name, sku, stock_quantity, warehouse
                FROM v_inventory_ai
                WHERE product_name LIKE ?
                LIMIT ?
                """;
        return agentJdbcTemplate.queryForList(sql, "%" + keyword + "%", limit);
    }
}
