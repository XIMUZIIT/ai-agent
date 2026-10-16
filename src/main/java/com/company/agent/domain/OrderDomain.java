package com.company.agent.domain;

import com.company.agent.core.DomainModule;
import com.company.agent.tool.DateTools;
import com.company.agent.tool.DomainToolFactory;
import com.company.agent.tool.OrderTools;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** 订单领域：只能访问 v_order_ai */
@Component
public class OrderDomain implements DomainModule {

    private static final Set<String> VIEWS = Set.of("v_order_ai");

    private final Object[] tools;

    public OrderDomain(OrderTools orderTools, DateTools dateTools, DomainToolFactory factory) {
        this.tools = new Object[]{
                orderTools,                  // 第 2 层：固定工具
                factory.metricTools(VIEWS),  // 第 1 层：语义指标（仅订单类指标）
                factory.sqlTool(VIEWS),      // 第 3 层：受限 SQL（仅订单视图）
                dateTools
        };
    }

    @Override
    public String name() {
        return "order";
    }

    @Override
    public String title() {
        return "订单";
    }

    @Override
    public String description() {
        return "订单明细、销售额、订单数、客单价、销售趋势";
    }

    @Override
    public List<String> keywords() {
        return List.of("订单", "销售额", "销量", "客单价", "成交", "下单", "退款", "支付金额");
    }

    @Override
    public String prompt() {
        return """
                【你的领域：订单与销售】
                工具选择优先级，必须严格遵守：
                1. 查询具体订单明细（如“金额最大的订单”）→ findLargestOrders。
                2. 销售额、订单数、客单价、趋势、分组对比 → 不确定指标时先 listMetrics，再 queryMetric。
                   口径以指标定义为准，不要自己改口径。
                3. 以上都无法满足的灵活分析 → executeQuery。只能查询视图，MySQL 语法，只写一条 SELECT：
                   v_order_ai(order_no 订单号, customer_name 客户名[已脱敏], product_category 商品品类,
                              channel 渠道, pay_amount 实付金额, status 状态[PAID/COMPLETED/CANCELLED/REFUNDED],
                              create_time 下单时间)
                   executeQuery 返回错误时，根据错误信息修正 SQL 重试，最多 2 次。
                """;
    }

    @Override
    public Object[] tools() {
        return tools;
    }
}
