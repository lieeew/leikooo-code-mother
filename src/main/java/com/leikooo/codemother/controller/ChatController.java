package com.leikooo.codemother.controller;

import com.leikooo.codemother.ai.advisor.LogAdvisor;
import com.leikooo.codemother.ai.advisor.SystemMessageFirstAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 测试 API 报错 400 最小 demo
 * @author leikooo
 */
@Deprecated
@RestController
@RequestMapping("/deprecated/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final JdbcChatMemoryRepository jdbcChatMemoryRepository;

    public ChatController(ChatClient.Builder chatClientBuilder, JdbcChatMemoryRepository jdbcChatMemoryRepository) {
        this.chatClient = chatClientBuilder.build();
        this.jdbcChatMemoryRepository = jdbcChatMemoryRepository;
    }

    @GetMapping(value = "/stream/{sessionId}")
    public Flux<String> stream(@PathVariable(name = "sessionId") String sessionId, @RequestParam(name = "message") String message) {
        MessageWindowChatMemory messageWindowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(100)
                .build();
        return chatClient.prompt()
                .system("You are a helpful assistant.")
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, sessionId))
                .advisors(MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build())
                .advisors(new SystemMessageFirstAdvisor(), new LogAdvisor())
                .stream()
                .content();
    }

}
