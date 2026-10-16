package com.company.agent.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 通用的同步领域 Agent 接口：系统提示词通过模板变量传入，
 * 所以所有领域共用这一个接口，不用为每个领域各写一个接口。
 */
public interface DomainAssistant {

    @SystemMessage("{{prompt}}")
    String chat(@MemoryId String memoryId, @V("prompt") String prompt, @UserMessage String userMessage);
}
