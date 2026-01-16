package com.leikooo.codemother.ai;

import com.leikooo.codemother.ai.advisor.*;
import com.leikooo.codemother.ai.tools.FileTools;
import com.leikooo.codemother.ai.tools.TodolistTools;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.enums.BuildResultEnum;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.service.ObservableRecordService;
import com.leikooo.codemother.service.SpringAiChatMemoryService;
import com.leikooo.codemother.service.ToolCallRecordService;
import lombok.Getter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.ThinkingTagCleaner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.leikooo.codemother.constant.AppConstant.GEN_APP_INFO;
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
    @Getter
    private final ChatClient chatClient;
    private final TodolistTools todolistTools;
    private final FileTools fileTools;
    private final ToolAdvisor toolAdvisor;
    private final MessageAggregatorAdvisor messageAggregatorAdvisor;
    private final BuildAdvisor buildAdvisor;
    private final ObservableRecordService observableRecordService;
    private final ToolCallRecordService toolCallRecordService;
    private final SpringAiChatMemoryService springAiChatMemoryService;
    private JdbcChatMemoryRepository chatMemoryRepository;

    public AiChatClient(ChatModel openAiChatModel, TodolistTools todolistTools, FileTools fileTools, ToolAdvisor toolAdvisor, MessageAggregatorAdvisor messageAggregatorAdvisor, BuildAdvisor buildAdvisor, ObservableRecordService observableRecordService, ToolCallRecordService toolCallRecordService, @Lazy SpringAiChatMemoryService springAiChatMemoryService, JdbcChatMemoryRepository chatMemoryRepository) {
        this.openAiChatModel = openAiChatModel;
        this.todolistTools = todolistTools;
        this.fileTools = fileTools;
        this.toolAdvisor = toolAdvisor;
        this.messageAggregatorAdvisor = messageAggregatorAdvisor;
        this.buildAdvisor = buildAdvisor;
        this.observableRecordService = observableRecordService;
        this.toolCallRecordService = toolCallRecordService;
        this.springAiChatMemoryService = springAiChatMemoryService;
        this.chatMemoryRepository = chatMemoryRepository;
        this.chatClient = ChatClient
                .builder(openAiChatModel)
                .defaultAdvisors(buildAdvisor, toolAdvisor, messageAggregatorAdvisor, buildAdvisor,
                        new SystemMessageFirstAdvisor(), new LogAdvisor(),
                        new ObservableAdvisor(observableRecordService))
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
                                .chatMemoryRepository(chatMemoryRepository)
                                .maxMessages(100)
                                .build())
                        .build())
                .call().content();
    }

    public BuildResultEnum checkBuildResult(String userMessage, Long appId) {
        String rawResponse = chatClient.prompt(userMessage)
                .system(new ClassPathResource("prompt/build-error-analysis-system-prompt.md"), UTF_8)
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, appId))
                .advisors(MessageChatMemoryAdvisor
                        .builder(MessageWindowChatMemory.builder()
                                .maxMessages(100)
                                .build())
                        .build())
                .call().content();
        ThinkingTagCleaner thinkingTagCleaner = new ThinkingTagCleaner();
        String normalizedResponse = normalizeEnumValue(thinkingTagCleaner.clean(rawResponse));
        return BuildResultEnum.fromDesc(normalizedResponse);
    }

    /**
     * 根据提示词生成 CodeGenTypeEnum
     *
     * @param prompt 用户提示词
     * @param appId appId
     * @return CodeGenTypeEnum
     */
    public CodeGenTypeEnum selectGenTypeEnum(String prompt, Long appId) {
        String rawResponse = chatClient.prompt(prompt)
                .system(new ClassPathResource("prompt/codegen-routing-system-prompt.md"), UTF_8)
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, appId))
                .advisors(MessageChatMemoryAdvisor
                        .builder(MessageWindowChatMemory.builder()
                                .maxMessages(100)
                                .build())
                        .build())
                .call().content();
        ThinkingTagCleaner thinkingTagCleaner = new ThinkingTagCleaner();
        String normalizedResponse = normalizeEnumValue(thinkingTagCleaner.clean(rawResponse));
        return CodeGenTypeEnum.getEnumByValue(normalizedResponse);
    }

    private String normalizeEnumValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.toLowerCase();
    }
}
