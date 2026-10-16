package com.company.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <p><b>Agent 服务启动类</b></p>
 * <p>放在 com.company.agent 根包下，组件扫描会覆盖 api / config / core / domain / semantic / tool 等所有子包。</p>
 * <p>如果你把这些代码合并进已有项目，请删掉本类，沿用已有的启动类，并确认它的包是 com.company.agent
 * 或其父包，否则需要加 scanBasePackages = "com.company.agent"。</p>
 */
@SpringBootApplication
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
