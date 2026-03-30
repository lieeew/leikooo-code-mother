package com.leikooo.codemother.config;

import com.leikooo.codemother.ai.advisor.*;
import com.leikooo.codemother.ai.tools.ContextTools;
import com.leikooo.codemother.ai.tools.FileTools;
import com.leikooo.codemother.ai.tools.TodolistTools;
import com.leikooo.codemother.service.ObservableRecordService;
import com.leikooo.codemother.service.ToolCallRecordService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * @author leikooo
 */
@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    public ChatModel primaryChatModel(@Qualifier("miniMaxChatModel") ChatModel miniMaxChatModel) {
        return miniMaxChatModel;
    }

    @Bean
    public ObservableAdvisor observableAdvisor(
            ObservableRecordService observableRecordService,
            ToolCallRecordService toolCallRecordService) {
        return new ObservableAdvisor(observableRecordService, toolCallRecordService);
    }


    @Bean
    public SystemMessageFirstAdvisor systemMessageFirstAdvisor() {
        return new SystemMessageFirstAdvisor();
    }

    @Bean
    public LogAdvisor logAdvisor() {
        return new LogAdvisor();
    }

    @Bean("codeGenChatClient")
    public ChatClient codeGenChatClient(
            ChatModel primaryChatModel,
            ToolAdvisor toolAdvisor,
            BuildAdvisor buildAdvisor,
            MessageAggregatorAdvisor messageAggregatorAdvisor,
            VersionAdvisor versionAdvisor,
            ContextCompressionAdvisor contextCompressionAdvisor,
            SystemMessageFirstAdvisor systemMessageFirstAdvisor,
            LogAdvisor logAdvisor,
            ObservableAdvisor observableAdvisor,
            TodolistTools todolistTools,
            FileTools fileTools,
            ExecuteToolAdvisor executeToolAdvisor,
            JdbcChatMemoryRepository chatMemoryRepository,
            ContextTools contextTools) {
        return ChatClient.builder(primaryChatModel)
                .defaultAdvisors(buildAdvisor, toolAdvisor,
                        messageAggregatorAdvisor, versionAdvisor,
                        contextCompressionAdvisor,
                        executeToolAdvisor,
                        systemMessageFirstAdvisor, logAdvisor,
                        MessageChatMemoryAdvisor
                                .builder(MessageWindowChatMemory.builder()
                                        .chatMemoryRepository(chatMemoryRepository)
                                        .maxMessages(100)
                                        .build())
                                .build(),
                        observableAdvisor)

                .defaultTools(todolistTools, fileTools, contextTools)
                .build();
    }

    @Bean("simpleChatClient")
    public ChatClient simpleChatClient(ChatModel primaryChatModel) {
        return ChatClient.builder(primaryChatModel).build();
    }

    @Bean("subAgentChatMemoryRepository")
    public ChatMemoryRepository subAgentChatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    /**
     * SubAgent 修复专用 ChatClient
     * 包含: fix-code-system-prompt.md + MessageChatMemoryAdvisor(maxMessages=100) + FileTools
     */
    @Bean("fixChatClient")
    public ChatClient fixChatClient(
            ChatModel primaryChatModel,
            ExecuteToolAdvisor executeToolAdvisor,
            SystemMessageFirstAdvisor systemMessageFirstAdvisor,
            FileTools fileTools,
            TodolistTools todolistTools,
            ToolAdvisor toolAdvisor,
            @Qualifier("subAgentChatMemoryRepository") ChatMemoryRepository subAgentChatMemoryRepository) {
        TodolistCacheCleanupAdvisor todolistCacheCleanupAdvisor = new TodolistCacheCleanupAdvisor();
        return ChatClient.builder(primaryChatModel)
                .defaultAdvisors(
                        executeToolAdvisor,
                        systemMessageFirstAdvisor,
                        toolAdvisor,
                        todolistCacheCleanupAdvisor,
                        MessageChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder()
                                        .chatMemoryRepository(subAgentChatMemoryRepository)
                                        .maxMessages(100)
                                        .build()
                        ).build()
                )
                .defaultTools(fileTools, todolistTools)
                .build();
    }
}
