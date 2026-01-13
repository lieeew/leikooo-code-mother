package com.leikooo.codemother.ai.tools;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author leikooo
 */
@Component
public class ToolEventPublisher {

    private final Map<String, Sinks.Many<ToolEvent>> sinks = new ConcurrentHashMap<>();

    private Sinks.Many<ToolEvent> getSink(String sessionId) {
        return sinks.computeIfAbsent(sessionId, k -> Sinks.many().multicast().onBackpressureBuffer());
    }

    public void publishToolCall(String sessionId, String toolName, String methodName, String toolCallId) {
        getSink(sessionId).tryEmitNext(new ToolEvent(sessionId, "tool_call", toolName, methodName, toolCallId, null));
    }

    public void publishToolResult(String sessionId, String toolName, String methodName, String toolCallId, Object result) {
        getSink(sessionId).tryEmitNext(new ToolEvent(sessionId, "tool_result", toolName, methodName, toolCallId, result));
    }

    public Flux<ToolEvent> events(String sessionId) {
        return getSink(sessionId).asFlux();
    }

    public void complete(String sessionId) {
        Sinks.Many<ToolEvent> sink = sinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    public record ToolEvent(String sessionId, String type, String toolName, String methodName, String toolCallId, Object result) {
    }
}
