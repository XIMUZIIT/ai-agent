package com.company.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.company.agent.repository.OrderQueryRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <p><b>订单固定工具（第 2 层）</b></p>
 * <p>工具只负责：参数校验与收敛、调用仓储、格式化结果。SQL 在 OrderQueryRepository 中。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTools {

    private final OrderQueryRepository orderRepository;

    @Tool("查询最近 N 天内实付金额最大的订单（仅统计已支付/已完成的有效订单），按金额从高到低返回前 topN 条。"
            + "用于“最近十天金额最大的订单”这类问题")
    public String findLargestOrders(
            @P("统计最近多少天，例如十天传 10") int days,
            @P("返回前几条，只要最大的一条传 1") int topN) {

        int safeDays = Math.min(Math.max(days, 1), 90);
        int safeTopN = Math.min(Math.max(topN, 1), 20);
        log.info("[Tool] findLargestOrders days={}, topN={}", safeDays, safeTopN);

        List<Map<String, Object>> rows =
                orderRepository.findLargestOrders(LocalDateTime.now().minusDays(safeDays), safeTopN);
        return rows.isEmpty() ? "最近 " + safeDays + " 天没有查询到有效订单" : JSON.toJSONString(rows);
    }
}
