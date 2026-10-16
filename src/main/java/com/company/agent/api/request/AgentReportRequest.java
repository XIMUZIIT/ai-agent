package com.company.agent.api.request;

import lombok.Data;

/**
 * <p><b>Web 接口请求传输 DTO</b></p>
 *
 * @author Enterprise Agent Platform Team
 * @version 2.0.0
 */
@Data
public class AgentReportRequest {
    private String prompt;
    private String sessionId;
}
