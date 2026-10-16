package com.company.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * <p><b>LLM 模型配置属性映射 Bean</b></p>
 * <p>说明：将配置中 agent.llm 前缀的配置项映射为属性，与逻辑解耦。</p>
 *
 * @author Enterprise Agent Platform Team
 * @version 2.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.llm")
public class AgentProperties {
    /** 大模型访问 API 密钥 */
    private String apiKey;
    /** 大模型 Base URL 网关地址 */
    private String baseUrl;
    /** 模型类型代码，如 gpt-4o */
    private String modelName = "gpt-4o";
    /** 路由（意图分类）专用模型，如 gpt-4o-mini；不配置则与 modelName 相同 */
    private String routerModelName;
    /** 随机采样温度，低温度确保 SQL 与 JSON 语法准确度 */
    private Double temperature = 0.1;
    /** 单次 LLM 调用超时上限 (秒) */
    private Long timeoutSeconds = 60L;
    /** 是否打印请求 HTTP 日志（请求里含表结构和业务数据，生产环境应保持 false） */
    private Boolean logRequests = false;
    /** 是否打印响应 HTTP 日志（同上） */
    private Boolean logResponses = false;
}
