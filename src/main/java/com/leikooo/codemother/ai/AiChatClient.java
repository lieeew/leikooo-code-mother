package com.leikooo.codemother.ai;

import com.leikooo.codemother.ai.advisor.ToolAdvisor;
import com.leikooo.codemother.ai.tools.FileTools;
import com.leikooo.codemother.ai.tools.TodolistTools;
import com.leikooo.codemother.model.dto.GenAppDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/31
 * @description
 */
@Component
public class AiChatClient {
    private final ChatModel openAiChatModel;
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final TodolistTools todolistTools;
    private final FileTools fileTools;

    public AiChatClient(ChatModel openAiChatModel, ChatMemory chatMemory, TodolistTools todolistTools, FileTools fileTools) {
        this.openAiChatModel = openAiChatModel;
        this.chatMemory = chatMemory;
        this.todolistTools = todolistTools;
        this.fileTools = fileTools;
        try {
            String systemPrompt = new ClassPathResource("prompt/generate-html-code.md").getContentAsString(UTF_8);
            this.chatClient = ChatClient
                    .builder(openAiChatModel)
                    .defaultSystem(systemPrompt)
                    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .defaultAdvisors(new ToolAdvisor())
                    .defaultTools(todolistTools, fileTools)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Flux<ChatClientResponse> generateCode(GenAppDto genAppDto) {
        String message = genAppDto.getMessage();
        String appId = genAppDto.getAppId();
        return chatClient.prompt(message)
                .tools()
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, appId))
                .toolContext(Map.of(CONVERSATION_ID, appId))
                .stream().chatClientResponse();
    }

    public String generateCodeFlux(String message) {
        return chatClient.prompt(message)
                .call().content();
    }
}
