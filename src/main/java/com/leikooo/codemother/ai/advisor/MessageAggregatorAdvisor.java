package com.leikooo.codemother.ai.advisor;

import com.leikooo.codemother.model.entity.SpringAiChatMemory;
import com.leikooo.codemother.service.SpringAiChatMemoryService;
import com.leikooo.codemother.utils.ConversationIdUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/1/15
 * @description
 */
@Slf4j
@Component
public class MessageAggregatorAdvisor implements CallAdvisor, StreamAdvisor {
    private final SpringAiChatMemoryService springAiChatMemoryService;

    public MessageAggregatorAdvisor(@Lazy SpringAiChatMemoryService springAiChatMemoryService) {
        this.springAiChatMemoryService = springAiChatMemoryService;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        return callAdvisorChain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        String appId = ConversationIdUtils.getConversationId(chatClientRequest.context());
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
                .doFinally(signalType -> updateUpdatedMessage(appId, resultCollector.toString()));
    }

    private Optional<String> extractText(ChatClientResponse response) {
        return Optional.ofNullable(response.chatResponse())
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .map(AbstractMessage::getText);
    }

    private void updateUpdatedMessage(String appId, String content) {
        try {
            SpringAiChatMemory springAiChatMemory = springAiChatMemoryService.lambdaQuery()
                    .eq(SpringAiChatMemory::getConversationId, appId)
                    .eq(SpringAiChatMemory::getType, MessageType.ASSISTANT)
                    .orderByDesc(SpringAiChatMemory::getTimestamp)
                    .last("LIMIT 1")
                    .one();
            if (Objects.isNull(springAiChatMemory)) {
                SpringAiChatMemory newRecord = SpringAiChatMemory.builder()
                        .conversationId(appId)
                        .type(MessageType.ASSISTANT)
                        .content(content)
                        .timestamp(new Date())
                        .build();
                springAiChatMemoryService.save(newRecord);
            } else {
                springAiChatMemoryService.lambdaUpdate()
                        .eq(SpringAiChatMemory::getConversationId, appId)
                        .eq(SpringAiChatMemory::getType, MessageType.ASSISTANT)
                        .set(SpringAiChatMemory::getContent, content)
                        .update();
            }
        } catch (Exception e) {
            log.error("保存包含工具调用的完整记录报错 {}", e);
        }
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
