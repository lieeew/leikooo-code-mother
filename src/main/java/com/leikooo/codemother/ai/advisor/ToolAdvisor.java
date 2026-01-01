package com.leikooo.codemother.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/31
 * @description
 */
@Slf4j
public class ToolAdvisor implements StreamAdvisor, CallAdvisor {

    @Override
    public String getName() {
        return "tool 工具调用回调";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        ChatClientRequest toolClient = chatClientRequest.copy();
        Map<String, Object> context = toolClient.context();
        System.out.println("context = " + context);
        Flux<ChatClientResponse> chatClientResponseFlux = streamAdvisorChain.nextStream(chatClientRequest);
        return chatClientResponseFlux.doOnNext(response -> {
            System.out.println("response = " + response);
        });

    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        Prompt prompt = chatClientRequest.prompt();
        log.info("prompt = {}", prompt);
        return callAdvisorChain.nextCall(chatClientRequest);
    }
}
