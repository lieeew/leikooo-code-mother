package com.leikooo.codemother.ai.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 确保 SYSTEM 信息始终在 [0] 位置，否者报错 400
 * @author leikooo
 */
@Slf4j
public class SystemMessageFirstAdvisor implements CallAdvisor, StreamAdvisor {

    private static final int DEFAULT_ORDER = Integer.MAX_VALUE - 1000;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientRequest newRequest = reorderSystemMessageFirst(chatClientRequest);
        return callAdvisorChain.nextCall(newRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        ChatClientRequest newRequest = reorderSystemMessageFirst(chatClientRequest);
        return streamAdvisorChain.nextStream(newRequest);
    }

    private ChatClientRequest reorderSystemMessageFirst(ChatClientRequest request) {
        Prompt prompt = request.prompt();
        List<Message> messages = new ArrayList<>(prompt.getInstructions());
        if (messages.size() <= 1) {
            return request;
        }
        messages.sort(Comparator.comparing(m -> !(m instanceof SystemMessage) ? 1 : 0));
        Prompt newPrompt = new Prompt(messages, prompt.getOptions());
        return new ChatClientRequest(newPrompt, request.context());
    }

    @Override
    public String getName() {
        return "SystemMessageFirstAdvisor";
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }
}
