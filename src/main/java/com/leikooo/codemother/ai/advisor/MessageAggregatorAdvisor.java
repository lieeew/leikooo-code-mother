package com.leikooo.codemother.ai.advisor;

import com.leikooo.codemother.model.dto.ChatContext;
import com.leikooo.codemother.model.enums.ChatHistoryMessageTypeEnum;
import com.leikooo.codemother.service.ChatHistoryService;
import com.leikooo.codemother.utils.ConversationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/1/15
 * @description
 */
@Slf4j
@Component
public class MessageAggregatorAdvisor implements CallAdvisor, StreamAdvisor {

    private final ChatHistoryService chatHistoryService;

    public MessageAggregatorAdvisor(@Lazy ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        return callAdvisorChain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        StringBuilder resultCollector = new StringBuilder();

        return streamAdvisorChain.nextStream(chatClientRequest)
                .doOnNext(response -> {
                    String text = response.chatResponse().getResults().stream()
                            .map(Generation::getOutput)
                            .map(AbstractMessage::getText)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining());
                    resultCollector.append(text);
                })
                .doFinally(signalType -> updateUpdatedMessage(ConversationUtils.getChatContext(chatClientRequest.context()), resultCollector.toString()));
    }

    private void updateUpdatedMessage(ChatContext chatContext, String content) {
        String appId = chatContext.appId();
        String userId = chatContext.userId();
        chatHistoryService.addChatMessage(appId, content, ChatHistoryMessageTypeEnum.AI.getValue(), userId);
    }

    @Override
    public String getName() {
        return "MessageAggregatorAdvisor";
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
}
