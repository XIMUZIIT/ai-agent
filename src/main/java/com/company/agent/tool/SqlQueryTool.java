package com.company.agent.tool;

import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p><b>第 3 层工具：受限 Text-to-SQL（兜底）</b></p>
 * <p>不是 Spring Bean：由 DomainToolFactory 按领域创建，每个实例的视图白名单不同。</p>
 * <p>护栏：① 只允许单条 SELECT ② 表必须在本领域白名单视图内 ③ 禁用危险关键字
 * ④ 外层强制 LIMIT ⑤ 查询超时 + 最大行数 ⑥ 审计日志
 * ⑦ 底层是只读账号，且只授权 AI 视图（最后一道防线）。</p>
 */
@Slf4j
public class SqlQueryTool {

    private static final Pattern FORBIDDEN =
            Pattern.compile("\\b(into|outfile|dumpfile|load_file|sleep|benchmark|information_schema)\\b");
    private static final int MAX_ROWS = 200;

    private final JdbcTemplate jdbc;
    private final Set<String> allowedViews;

    public SqlQueryTool(JdbcTemplate agentJdbcTemplate, Set<String> allowedViews) {
        // 独立实例，设置自己的超时和行数上限，不影响其他工具共用的 JdbcTemplate
        this.jdbc = new JdbcTemplate(agentJdbcTemplate.getDataSource());
        this.jdbc.setQueryTimeout(10);
        this.jdbc.setMaxRows(MAX_ROWS);
        this.allowedViews = allowedViews.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Tool("执行只读 SELECT 查询，仅在固定工具和指标工具都无法满足时使用。只能查询系统提示中列出的视图，MySQL 语法")
    public String executeQuery(@P("单条 MySQL SELECT 语句，不要以分号结尾") String sql) {
        log.info("[SQL-AUDIT] views={} sql={}", allowedViews, sql);
        try {
            if (sql == null || sql.isBlank()) {
                return "错误：SQL 不能为空";
            }
            String cleaned = sql.trim().replaceAll(";+\\s*$", "");
            if (FORBIDDEN.matcher(cleaned.toLowerCase(Locale.ROOT)).find()) {
                return "错误：SQL 包含禁止使用的关键字";
            }
            Statement stmt = CCJSqlParserUtil.parse(cleaned);
            if (!(stmt instanceof Select)) {
                return "错误：只允许 SELECT 语句";
            }
            for (String t : extractTables(stmt)) {
                String name = t.replace("`", "").toLowerCase(Locale.ROOT);
                if (!allowedViews.contains(name)) {
                    return "错误：不允许访问表 " + t + "，可用视图：" + allowedViews;
                }
            }
            List<Map<String, Object>> rows =
                    jdbc.queryForList("SELECT * FROM (" + cleaned + ") t LIMIT " + MAX_ROWS);
            return rows.isEmpty() ? "没有查询到数据" : JSON.toJSONString(rows);
        } catch (Exception e) {
            // 错误信息回给模型，让它修正后重试（主流的自纠错做法）
            log.warn("[SQL-AUDIT] 执行失败: {}", e.getMessage());
            return "SQL 执行失败：" + e.getMessage();
        }
    }

    /**
     * 提取 SQL 中引用的所有表名。
     * <p>jsqlparser 各版本的方法名和返回类型不一致（有的叫 getTableList 返回 List，有的叫 getTables 返回 Set，
     * 5.x 的 TablesNamesFinder 还是泛型类），直接调用会在部分版本上编译失败，所以这里用反射按顺序尝试。
     * 两个方法都不存在时抛出异常，调用方会把它当作"校验失败"拒绝执行（宁可拒绝，不放行）。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection<String> extractTables(Statement stmt) throws Exception {
        Object finder = new TablesNamesFinder();
        for (String methodName : new String[]{"getTableList", "getTables"}) {
            try {
                Method method = TablesNamesFinder.class.getMethod(methodName, Statement.class);
                Object result = method.invoke(finder, stmt);
                if (result instanceof Collection) {
                    return (Collection<String>) result;
                }
            } catch (NoSuchMethodException ignored) {
                // 当前版本没有这个方法，继续尝试下一个
            }
        }
        throw new IllegalStateException("当前 jsqlparser 版本不支持表名提取，无法校验 SQL");
    }
}
