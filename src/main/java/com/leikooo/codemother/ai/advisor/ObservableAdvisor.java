package com.leikooo.codemother.ai.advisor;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.leikooo.codemother.model.entity.ObservableRecord;
import com.leikooo.codemother.service.ObservableRecordService;
import com.leikooo.codemother.service.ToolCallRecordService;
import com.leikooo.codemother.utils.ConversationIdUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.context.annotation.Lazy;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author leikooo
 * @description 可观测性 advisor - 记录 token 消耗量和生成时间
 */
@Slf4j
public class ObservableAdvisor implements CallAdvisor, StreamAdvisor {

    private final Encoding encoding;
    private final ObservableRecordService observableRecordService;
    private final ToolCallRecordService toolCallRecordService;

    public ObservableAdvisor(ObservableRecordService observableRecordService, ToolCallRecordService toolCallRecordService) {
        this.toolCallRecordService = toolCallRecordService;
        this.observableRecordService = observableRecordService;
        this.encoding = Encodings.newDefaultEncodingRegistry()
                .getEncoding(EncodingType.CL100K_BASE);
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long startTime = System.currentTimeMillis();

        ChatClientResponse response = chain.nextCall(request);

        recordUsage(request, response.chatResponse(), startTime);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        long startTime = System.currentTimeMillis();
        String conversationId = ConversationIdUtils.getConversationId(request.context());
        long startToolCallCount = getCurrentToolCallCount(conversationId);

        StringBuilder contentAccumulator = new StringBuilder();
        AtomicLong promptTokens = new AtomicLong(0);
        AtomicLong completionTokens = new AtomicLong(0);

        return chain.nextStream(request)
                .doOnNext(response -> {
                    Generation result = response.chatResponse().getResult();
                    if (result.getOutput().getText() != null) {
                        contentAccumulator.append(result.getOutput().getText());
                    }
                    Usage usage = response.chatResponse().getMetadata().getUsage();
                    if (usage != null) {
                        promptTokens.set(usage.getPromptTokens());
                        completionTokens.set(usage.getCompletionTokens());
                    }
                })
                .doFinally(signal -> {
                    Long pTokens = promptTokens.get() > 0 ? promptTokens.get() : null;
                    Long cTokens = completionTokens.get() > 0 ? completionTokens.get() : null;

                    pTokens = Optional.ofNullable(pTokens).orElse((long) encoding.countTokens(request.prompt().getContents()));
                    cTokens = Optional.ofNullable(cTokens).orElse((long) encoding.countTokens(contentAccumulator.toString()));

                    long endToolCallCount = getCurrentToolCallCount(conversationId);
                    long toolCallCount = Math.max(0, endToolCallCount - startToolCallCount);

                    saveRecord(conversationId, pTokens, cTokens, toolCallCount, startTime);
                });
    }

    private long getCurrentToolCallCount(String conversationId) {
        try {
            return toolCallRecordService.countBySessionId(conversationId);
        } catch (Exception e) {
            log.warn("Failed to count tool calls for session {}", conversationId, e);
            return 0;
        }
    }

    private void recordUsage(ChatClientRequest request, ChatResponse response, long startTime) {
        Usage usage = response.getMetadata().getUsage();
        String conversationId = ConversationIdUtils.getConversationId(request.context());
        long startToolCallCount = getCurrentToolCallCount(conversationId);

        long pTokens;
        long cTokens;

        if (usage != null) {
            pTokens = usage.getPromptTokens();
            cTokens = usage.getCompletionTokens();
        } else {
            log.warn("[ObservableAdvisor] Usage metadata missing, falling back to manual calculation.");
            pTokens = encoding.countTokens(request.prompt().getContents());
            String outputContent = response.getResult().getOutput().getText();
            cTokens = encoding.countTokens(outputContent);
        }

        long endToolCallCount = getCurrentToolCallCount(conversationId);
        long toolCallCount = Math.max(0, endToolCallCount - startToolCallCount);

        saveRecord(conversationId, pTokens, cTokens, toolCallCount, startTime);
    }

    private void saveRecord(String conversationId, long promptTokens, long completionTokens, long toolCallCount, long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        try {
            ObservableRecord entity = ObservableRecord.builder()
                    .conversationId(conversationId)
                    .inputTokens(promptTokens)
                    .outputTokens(completionTokens)
                    .durationMs(duration)
                    .toolCallCount((int) toolCallCount)
                    .timestamp(Date.from(Instant.now()))
                    .build();

            observableRecordService.save(entity);

            log.info("[TokenUsage] ID: {}, Input: {}, Output: {}, Time: {}ms, ToolCalls: {}",
                    conversationId, promptTokens, completionTokens, duration, toolCallCount);
        } catch (Exception e) {
            log.error("Failed to save usage record", e);
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getName() {
        return "ObservableAdvisor";
    }
}
