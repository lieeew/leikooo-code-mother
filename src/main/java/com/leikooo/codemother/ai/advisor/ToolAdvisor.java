package com.leikooo.codemother.ai.advisor;

import com.leikooo.codemother.ai.tools.ToolEventPublisher;
import com.leikooo.codemother.utils.ConversationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/31
 * @description
 */
@Slf4j
@Component
public class ToolAdvisor implements CallAdvisor, StreamAdvisor {

    private final ToolEventPublisher toolEventPublisher;

    public ToolAdvisor(ToolEventPublisher toolEventPublisher) {
        this.toolEventPublisher = toolEventPublisher;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        return callAdvisorChain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        String appId = ConversationUtils.getChatContext(chatClientRequest.context()).appId();
        Flux<ChatClientResponse> toolEventFlux = getToolEventFlux(appId);
        Flux<ChatClientResponse> mainFlux = streamAdvisorChain.nextStream(chatClientRequest)
                .doFinally(signalType -> toolEventPublisher.complete(appId));
        return Flux.merge(mainFlux, toolEventFlux);
    }

    @Override
    public String getName() {
        return "ToolAdvisor";
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE + 100;
    }

    /**
     * 工具调用推送流
     * @param sessionId sessionId
     * @return flux
     */
    private Flux<ChatClientResponse> getToolEventFlux(String sessionId) {
        return toolEventPublisher.events(sessionId)
                .map(event -> {
                    Object result = Optional.ofNullable(event.result()).orElse("");
                    final String methodName = event.methodName();
                    String message = switch (event.type()) {
                        case "tool_call" -> String.format("正在进行工具调用 %s: %s", methodName, result);
                        case "tool_result" -> String.format("工具调用完成 %s: %s", methodName, result);
                        default -> "";
                    };
                    return String.format("\n\n[选择工具] %s \n\n", message);
                }).map(message -> {
                    AssistantMessage assistantMessage = new AssistantMessage(message);
                    Generation generation = new Generation(assistantMessage);
                    ChatResponse chatResponse = ChatResponse.builder()
                            .generations(List.of(generation))
                            .build();
                    return ChatClientResponse.builder().chatResponse(chatResponse).build();
                });
    }

}
