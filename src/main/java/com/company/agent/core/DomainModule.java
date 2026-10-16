package com.company.agent.core;

import java.util.List;

/**
 * <p><b>领域模块：新增一个领域 = 新增一个实现类，其他代码不用改</b></p>
 * <p>Spring 会自动收集所有实现类，AgentRegistry 据此生成对应的 Agent 和路由提示词。</p>
 * <p>领域隔离：每个领域自己创建工具实例，并通过 DomainToolFactory 限定可访问的视图，
 * 所以订单 Agent 查不到库存视图，反之亦然。</p>
 */
public interface DomainModule {

    /** 领域代号，英文小写，路由模型会输出它，如 order / inventory / delivery。other、clarify、route_failed 为保留字 */
    String name();

    /** 简短中文名，多领域合并回答时作为小标题，如“订单”“库存” */
    String title();

    /** 一句话职责说明，会写进路由提示词和"无关问题"的引导话术 */
    String description();

    /** 该领域专属的提示词（工具选择规则、可查视图结构等） */
    String prompt();

    /** 该领域可用的工具对象（带 @Tool 方法的对象，范围只限本领域） */
    Object[] tools();

    /**
     * 路由兜底关键词：路由模型调用失败，或输出无法解析、判为“需要追问”时，
     * 只要用户问题里包含其中任意一个词，就直接分到本领域。默认为空（该领域不参与关键词兜底）。
     * 建议只放有明确指向性的词，避免和其他领域冲突。
     */
    default List<String> keywords() {
        return List.of();
    }
}
