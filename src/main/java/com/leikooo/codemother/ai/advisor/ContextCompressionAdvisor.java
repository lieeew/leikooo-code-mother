package com.leikooo.codemother.ai.advisor;

import com.leikooo.codemother.model.dto.ChatContext;
import com.leikooo.codemother.utils.ConversationUtils;
import com.leikooo.codemother.utils.TokenEstimator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @description 上下文自动压缩 Advisor，token 用量超 50% 时自动 summarize 旧消息
 */
@Slf4j
@Component
public class ContextCompressionAdvisor implements CallAdvisor, StreamAdvisor {

    private static final int ORDER = 200;
    private static final int MAX_CONTEXT_TOKENS = 20_000;
    private static final double COMPRESSION_THRESHOLD = 0.5;
    private static final int RECENT_MESSAGE_COUNT = 20;
    private static final int TOKEN_THRESHOLD = (int) (MAX_CONTEXT_TOKENS * COMPRESSION_THRESHOLD);

    private static final String SUMMARY_PROMPT = """
            Summarize this conversation concisely. \
            Preserve: key decisions, file paths, code structure, current task state, unresolved issues. \
            Same language as conversation. Max 500 words.
            
            """;

    private final ChatModel chatModel;
    private final JdbcChatMemoryRepository chatMemoryRepository;

    public ContextCompressionAdvisor(@Lazy ChatModel chatModel, JdbcChatMemoryRepository chatMemoryRepository) {
        this.chatModel = chatModel;
        this.chatMemoryRepository = chatMemoryRepository;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(compressIfNeeded(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(compressIfNeeded(request));
    }

    private ChatClientRequest compressIfNeeded(ChatClientRequest request) {
        try {
            List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
            int totalTokens = TokenEstimator.estimateTokens(messages);

            if (totalTokens <= TOKEN_THRESHOLD) {
                return request;
            }

            log.info("[ContextCompression] Token usage {} ({:.1f}%) exceeds threshold, compressing...",
                    totalTokens, (totalTokens * 100.0 / MAX_CONTEXT_TOKENS));

            List<Message> systemMessages = messages.stream()
                    .filter(m -> m instanceof SystemMessage).toList();
            List<Message> conversationMessages = messages.stream()
                    .filter(m -> !(m instanceof SystemMessage)).toList();

            if (conversationMessages.size() <= RECENT_MESSAGE_COUNT) {
                return request;
            }

            int splitIndex = conversationMessages.size() - RECENT_MESSAGE_COUNT;
            List<Message> oldMessages = conversationMessages.subList(0, splitIndex);
            List<Message> recentMessages = conversationMessages.subList(splitIndex, conversationMessages.size());

            String summary = generateSummary(oldMessages);

            List<Message> newMessages = new ArrayList<>(systemMessages);
            newMessages.add(new UserMessage("[Context Summary]\n" + summary));
            newMessages.addAll(recentMessages);

            // 持久化压缩后的消息
            persistCompressedMemory(request, newMessages);

            Prompt newPrompt = new Prompt(newMessages, request.prompt().getOptions());
            int newTokens = TokenEstimator.estimateTokens(newMessages);
            log.info("[ContextCompression] Compressed from {} to {} tokens", totalTokens, newTokens);

            return new ChatClientRequest(newPrompt, request.context());
        } catch (Exception e) {
            log.error("[ContextCompression] Failed to compress, passing through original request", e);
            return request;
        }
    }

    private String generateSummary(List<Message> messages) {
        StringBuilder content = new StringBuilder();
        for (Message msg : messages) {
            String role = msg instanceof UserMessage ? "User" : "Assistant";
            content.append(role).append(": ").append(msg.getText()).append("\n");
        }
        Prompt prompt = new Prompt(SUMMARY_PROMPT + content);
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    private void persistCompressedMemory(ChatClientRequest request, List<Message> newMessages) {
        try {
            ChatContext chatContext = ConversationUtils.getChatContext(request.context());
            String conversationId = chatContext.appId();
            chatMemoryRepository.deleteByConversationId(conversationId);
            List<Message> memoryMessages = newMessages.stream()
                    .filter(m -> !(m instanceof SystemMessage))
                    .toList();
            chatMemoryRepository.saveAll(conversationId, memoryMessages);
        } catch (Exception e) {
            log.error("[ContextCompression] Failed to persist compressed memory", e);
        }
    }

    @Override
    public String getName() {
        return "ContextCompressionAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
