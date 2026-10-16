package com.company.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.company.agent.repository.InventoryQueryRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 库存固定工具（第 2 层）：只做参数收敛和结果格式化，SQL 在 InventoryQueryRepository 中 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryTools {

    private final InventoryQueryRepository inventoryRepository;

    @Tool("根据商品名称关键字查询当前库存，例如“可乐”。返回匹配商品的库存数量和仓库")
    public String queryStock(@P("商品名称关键字，例如 可乐") String productName) {
        if (productName == null || productName.isBlank()) {
            return "错误：商品名称不能为空";
        }
        log.info("[Tool] queryStock productName={}", productName);

        List<Map<String, Object>> rows = inventoryRepository.findByProductName(productName.trim(), 20);
        return rows.isEmpty() ? "没有找到名称包含“" + productName + "”的商品库存" : JSON.toJSONString(rows);
    }
}
