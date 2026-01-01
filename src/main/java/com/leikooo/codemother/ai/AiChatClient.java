package com.leikooo.codemother.ai;

import com.leikooo.codemother.ai.advisor.ToolAdvisor;
import com.leikooo.codemother.ai.tools.FileTools;
import com.leikooo.codemother.ai.tools.TodolistTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

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
//                    .defaultAdvisors(s -> s.param(CONVERSATION_ID, "123456"))
                    .defaultAdvisors(new ToolAdvisor())
                    .defaultTools(todolistTools, fileTools)
//                    .defaultToolContext(Map.of(CONVERSATION_ID, "123456"))
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Flux<ChatClientResponse> generateCode(Prompt prompt) {
        return chatClient.prompt(prompt)
                .tools()
                .stream().chatClientResponse();
    }

    public String generateCode(String message) {
        return chatClient.prompt(message)
//                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, "123456"))
//                .toolContext(Map.of(CONVERSATION_ID, "123456"))
                .call().content();
    }
}
