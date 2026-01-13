package com.leikooo.codemother.ai;

import com.leikooo.codemother.ai.advisor.SystemMessageFirstAdvisor;
import com.leikooo.codemother.ai.advisor.ToolAdvisor;
import com.leikooo.codemother.ai.tools.FileTools;
import com.leikooo.codemother.ai.tools.TodolistTools;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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
    private final TodolistTools todolistTools;
    private final FileTools fileTools;
    private JdbcChatMemoryRepository chatMemoryRepository;
    public AiChatClient(ChatModel openAiChatModel, TodolistTools todolistTools, FileTools fileTools, JdbcChatMemoryRepository chatMemoryRepository) {
        this.openAiChatModel = openAiChatModel;
        this.todolistTools = todolistTools;
        this.fileTools = fileTools;
        this.chatMemoryRepository = chatMemoryRepository;
        this.chatClient = ChatClient
                .builder(openAiChatModel)
                .defaultAdvisors(new ToolAdvisor(), new SystemMessageFirstAdvisor())
                .defaultTools(todolistTools, fileTools)
                .build();
    }

    public Flux<ChatClientResponse> generateCode(GenAppDto genAppDto) {
        String message = genAppDto.getMessage();
        String appId = genAppDto.getAppId();
        CodeGenTypeEnum codeGenTypeEnum = genAppDto.getCodeGenTypeEnum();
        ClassPathResource classPathResource = getClassPathResource(codeGenTypeEnum);
        return chatClient.prompt()
                .tools()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, appId))
                .advisors(MessageChatMemoryAdvisor
                        .builder(MessageWindowChatMemory.builder()
                                .chatMemoryRepository(chatMemoryRepository)
                                .maxMessages(100)
                                .build())
                        .build())
                .system(classPathResource, UTF_8)
                .toolContext(Map.of(CONVERSATION_ID, appId))
                .stream().chatClientResponse();
    }

    /**
     * 根据 codeGenTypeEnum 返回 ClassPathResource
     * @param codeGenTypeEnum codeGenTypeEnum
     * @return ClassPathResource
     */
    private ClassPathResource getClassPathResource(CodeGenTypeEnum codeGenTypeEnum) {
        ClassPathResource classPathResource = null;
        switch (codeGenTypeEnum) {
            case HTML -> classPathResource = new ClassPathResource("prompt/generate-html-code.md");
            case MULTI_FILE -> classPathResource = new ClassPathResource("prompt/codegen-multi-file-system-prompt.md");
            case VUE_PROJECT ->
                    classPathResource = new ClassPathResource("prompt/codegen-vue-project-system-prompt.md");
            default -> throw new IllegalStateException("Unexpected value: " + codeGenTypeEnum);
        }
        return classPathResource;
    }

    public String generateCodeFlux(String message) {
        return chatClient.prompt(message)
                .advisors(MessageChatMemoryAdvisor
                        .builder(MessageWindowChatMemory.builder()
                                .maxMessages(10)
                                .build())
                        .build())
                .call().content();
    }

    /**
     * 根据提示词生成 CodeGenTypeEnum
     * @param prompt 用户提示词
     * @return CodeGenTypeEnum
     */
    public CodeGenTypeEnum selectGenTypeEnum(String prompt) {
        return chatClient.prompt(prompt)
                .system(new ClassPathResource("prompt/codegen-routing-system-prompt.md"), UTF_8)
                .advisors(MessageChatMemoryAdvisor
                        .builder(MessageWindowChatMemory.builder()
                                .maxMessages(10)
                                .build())
                        .build())
                .call()
                .responseEntity(CodeGenTypeEnum.class).getEntity();

    }
}
