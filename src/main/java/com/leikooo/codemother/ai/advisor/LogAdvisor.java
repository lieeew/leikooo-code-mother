package com.leikooo.codemother.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/1/1
 * @description 输出日志 log
 */
@Slf4j
public class LogAdvisor implements StreamAdvisor,  CallAdvisor {
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        log.info("log advisor before request {}", callAdvisorChain);
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        log.info("log advisor after request {}", chatClientResponse);
        return chatClientResponse;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        log.info("stream log advisor before request {}", chatClientRequest);
        return streamAdvisorChain.nextStream(chatClientRequest);
    }

    @Override
    public String getName() {
        return "LogAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
