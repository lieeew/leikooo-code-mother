package com.leikooo.codemother.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/31
 * @description
 */
@Slf4j
public class ToolAdvisor extends ToolCallAdvisor {

    public ToolAdvisor() {
        super(DefaultToolCallingManager.builder().build(), 0);
    }

    /**
     * before tool calling
     * @param chatClientRequest
     * @param callAdvisorChain
     * @return
     */
    @Override
    protected ChatClientRequest doBeforeCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        Prompt prompt = chatClientRequest.copy().prompt();
        log.info("before tool calling {}", prompt);
        return super.doBeforeCall(chatClientRequest, callAdvisorChain);
    }

    /**
     * after tool calling
     * @param chatClientResponse
     * @param callAdvisorChain
     * @return
     */
    @Override
    protected ChatClientResponse doAfterCall(ChatClientResponse chatClientResponse, CallAdvisorChain callAdvisorChain) {
        log.info("after tool call !");
        ChatResponse chatResponse = chatClientResponse.copy().chatResponse();
        if (chatResponse.hasToolCalls()) {
            List<AssistantMessage.ToolCall> toolCalls = chatClientResponse.chatResponse().getResult().getOutput().getToolCalls();
            toolCalls.forEach(toolCall -> {
                log.info("tool calling {}", toolCall);
            });
        }
        return super.doAfterCall(chatClientResponse, callAdvisorChain);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        Flux<ChatClientResponse> chatClientResponseFlux = streamAdvisorChain.nextStream(chatClientRequest);
        return chatClientResponseFlux.doOnNext(response -> {
            log.info("res: {}", response);
        });
    }
}
