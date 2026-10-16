package com.company.agent.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * <p><b>AI 专用只读数据源</b></p>
 * <p>本服务所有工具（固定工具、语义层、受限 SQL）都只通过这个数据源访问数据库。
 * 数据库账号只应授权 v_order_ai / v_inventory_ai 等 AI 视图，
 * 这样即使模型被诱导，也读不到视图以外的数据。</p>
 * <p>配置优先级：① agent.db.*（推荐，只读账号）；② 未配置时回退到 spring.datasource.*（仅为了先跑通，
 * 会打印警告，生产环境请务必改用只读账号）；③ 两者都没有则启动失败，并给出明确的配置提示。</p>
 * <p>注意：定义了 DataSource Bean 后，Spring Boot 不再自动创建默认数据源。
 * 如果本服务还需要读写业务库，请另行配置多数据源。</p>
 */
@Slf4j
@Configuration
public class AgentDataSourceConfig {

    @Bean
    public DataSource agentReadOnlyDataSource(AgentDbProperties p, Environment env) {
        String url = p.getUrl();
        String username = p.getUsername();
        String password = p.getPassword();
        String driver = p.getDriverClassName();

        if (isBlank(url)) {
            String fallbackUrl = env.getProperty("spring.datasource.url");
            if (isBlank(fallbackUrl)) {
                throw new IllegalStateException(
                        "未配置 AI 数据源：请在 application.yml 或 Nacos 中配置 agent.db.url、agent.db.username、"
                                + "agent.db.password（建议使用只授权 AI 视图的只读账号），"
                                + "或至少配置 spring.datasource.url。配置示例见项目 application.yml 的 agent.db 段");
            }
            log.warn("[AgentDataSource] 未配置 agent.db.url，已回退使用 spring.datasource.*。"
                    + "这意味着 AI 工具使用的是业务库账号，生产环境请改为只读账号并配置 agent.db.*");
            url = fallbackUrl;
            username = env.getProperty("spring.datasource.username");
            password = env.getProperty("spring.datasource.password");
            driver = env.getProperty("spring.datasource.driver-class-name", driver);
        }

        HikariConfig c = new HikariConfig();
        c.setPoolName("agent-readonly-pool");
        c.setJdbcUrl(url);
        c.setUsername(username);
        c.setPassword(password);
        c.setDriverClassName(driver);
        c.setMaximumPoolSize(p.getMaxPoolSize());
        c.setReadOnly(true);
        c.setConnectionTimeout(5_000);
        // 小于 0：启动时不强制连库，连接池先起来，库暂时不可用也不影响服务启动，
        // 只有真正执行查询时才会报错（HikariCP 默认启动即连库，连不上就直接启动失败）
        c.setInitializationFailTimeout(-1);
        return new HikariDataSource(c);
    }

    @Bean
    public JdbcTemplate agentJdbcTemplate(DataSource agentReadOnlyDataSource) {
        return new JdbcTemplate(agentReadOnlyDataSource);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
