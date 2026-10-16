package com.company.agent.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/** 路由 Agent：不挂工具、不挂记忆，只输出领域代号，调用快、成本低 */
public interface RouterAssistant {

    @SystemMessage("{{rules}}")
    String classify(@V("rules") String rules, @UserMessage String input);
}
