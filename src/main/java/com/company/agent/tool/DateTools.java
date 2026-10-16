package com.company.agent.tool;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** 让模型获取准确的当前日期，处理"本月""上周""最近7天"等相对时间 */
@Component
public class DateTools {

    @Tool("获取今天的日期（yyyy-MM-dd）和星期，处理本月、上周、最近N天等相对时间前必须先调用")
    public String today() {
        LocalDate d = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        return d + " " + d.getDayOfWeek();
    }
}
