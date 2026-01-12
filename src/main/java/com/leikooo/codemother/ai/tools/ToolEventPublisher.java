package com.leikooo.codemother.ai.tools;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * @author leikooo
 */
@Component
public class ToolEventPublisher {

    private final Sinks.Many<ToolEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void publishToolCall(String sessionId, String toolName, String methodName, String toolCallId) {
        sink.tryEmitNext(new ToolEvent(sessionId, "tool_call", toolName, methodName, toolCallId, null));
    }

    public void publishToolResult(String sessionId, String toolName, String methodName, String toolCallId, Object result) {
        sink.tryEmitNext(new ToolEvent(sessionId, "tool_result", toolName, methodName, toolCallId, result));
    }

    public Flux<ToolEvent> events(String sessionId) {
        return sink.asFlux()
                .filter(event -> sessionId.equals(event.sessionId()));
    }

    public record ToolEvent(String sessionId, String type, String toolName, String methodName, String toolCallId, Object result) {
    }
}
