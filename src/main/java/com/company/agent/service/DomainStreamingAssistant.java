package com.company.agent.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/** 通用的流式领域 Agent 接口 */
public interface DomainStreamingAssistant {

    @SystemMessage("{{prompt}}")
    TokenStream chatStream(@MemoryId String memoryId, @V("prompt") String prompt, @UserMessage String userMessage);
}
