package com.company.agent.api;

import com.company.agent.api.request.AgentReportRequest;
import com.company.agent.core.AgentDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * <p><b>Agent 统一入口：不关心具体领域，由 AgentDispatcher 路由分发</b></p>
 */
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentDispatcher dispatcher;

    @PostMapping("/chat")
    public Mono<String> chat(@RequestBody AgentReportRequest request) {
        String sessionId = resolveSessionId(request);
        // 同步调用是阻塞的（路由 + 工具 + 模型），必须切到弹性线程池
        return Mono.fromCallable(() -> dispatcher.chat(sessionId, request.getPrompt()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody AgentReportRequest request) {
        return dispatcher.stream(resolveSessionId(request), request.getPrompt());
    }

    private String resolveSessionId(AgentReportRequest request) {
        return (request.getSessionId() != null && !request.getSessionId().isBlank())
                ? request.getSessionId()
                : UUID.randomUUID().toString();
    }
}
