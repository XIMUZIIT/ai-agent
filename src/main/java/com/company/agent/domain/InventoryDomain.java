package com.company.agent.domain;

import com.company.agent.core.DomainModule;
import com.company.agent.tool.DateTools;
import com.company.agent.tool.DomainToolFactory;
import com.company.agent.tool.InventoryTools;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** 库存领域：只能访问 v_inventory_ai */
@Component
public class InventoryDomain implements DomainModule {

    private static final Set<String> VIEWS = Set.of("v_inventory_ai");

    private final Object[] tools;

    public InventoryDomain(InventoryTools inventoryTools, DateTools dateTools, DomainToolFactory factory) {
        this.tools = new Object[]{
                inventoryTools,              // 第 2 层：固定工具
                factory.metricTools(VIEWS),  // 第 1 层：语义指标（仅库存类指标）
                factory.sqlTool(VIEWS),      // 第 3 层：受限 SQL（仅库存视图）
                dateTools
        };
    }

    @Override
    public String name() {
        return "inventory";
    }

    @Override
    public String title() {
        return "库存";
    }

    @Override
    public String description() {
        return "商品库存数量、各仓库库存";
    }

    @Override
    public List<String> keywords() {
        return List.of("库存", "仓库", "缺货", "存货", "sku");
    }

    @Override
    public String prompt() {
        return """
                【你的领域：库存】
                工具选择优先级，必须严格遵守：
                1. 查询某个商品的当前库存（如“可乐还有多少”）→ queryStock。
                2. 按仓库、商品汇总库存 → 不确定指标时先 listMetrics，再 queryMetric（指标 stock_quantity）。
                3. 以上都无法满足的灵活分析 → executeQuery。只能查询视图，MySQL 语法，只写一条 SELECT：
                   v_inventory_ai(product_name 商品名, sku 编码, stock_quantity 库存数量, warehouse 仓库)
                   executeQuery 返回错误时，根据错误信息修正 SQL 重试，最多 2 次。
                """;
    }

    @Override
    public Object[] tools() {
        return tools;
    }
}
