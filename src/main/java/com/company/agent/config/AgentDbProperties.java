package com.company.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 专用只读数据源配置（spring.datasource 前缀，考虑是否需要这样做） */
@Data
@Component
@ConfigurationProperties(prefix = "spring.datasource")
public class AgentDbProperties {
    private String url;
    private String username;
    private String password;
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
    private Integer maxPoolSize = 5;
}
