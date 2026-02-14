package com.leikooo.codemother.ai;

import com.leikooo.codemother.ai.advisor.*;
import com.leikooo.codemother.ai.tools.ContextTools;
import com.leikooo.codemother.ai.tools.FileTools;
import com.leikooo.codemother.ai.tools.TodolistTools;
import com.leikooo.codemother.model.dto.ChatContext;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.dto.ToolsContext;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.service.ObservableRecordService;
import com.leikooo.codemother.service.ToolCallRecordService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.ThinkingTagCleaner;
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
    private final ChatClient chatClient;
    private JdbcChatMemoryRepository chatMemoryRepository;
    private final ChatModel openAiChatModel;
    private final FileTools fileTools;

    public AiChatClient(ChatModel openAiChatModel, TodolistTools todolistTools, FileTools fileTools, ContextTools contextTools, ToolAdvisor toolAdvisor, MessageAggregatorAdvisor messageAggregatorAdvisor, BuildAdvisor buildAdvisor, ObservableRecordService observableRecordService, ToolCallRecordService toolCallRecordService, JdbcChatMemoryRepository chatMemoryRepository, VersionAdvisor versionAdvisor, ContextCompressionAdvisor contextCompressionAdvisor) {
        this.chatMemoryRepository = chatMemoryRepository;
        this.openAiChatModel = openAiChatModel;
        this.fileTools = fileTools;
        this.chatClient = ChatClient
                .builder(openAiChatModel)
                .defaultAdvisors(buildAdvisor, toolAdvisor, messageAggregatorAdvisor, versionAdvisor,
                        contextCompressionAdvisor,
                        new SystemMessageFirstAdvisor(), new LogAdvisor(),
                        new ObservableAdvisor(observableRecordService, toolCallRecordService))
                .defaultTools(todolistTools, fileTools, contextTools)
                .build();
    }

    public Flux<String> generateCode(GenAppDto genAppDto) {
        String message = genAppDto.getMessage();
        String appId = genAppDto.getAppId();
        String userId = genAppDto.getUserLogin().getId();
        CodeGenTypeEnum codeGenTypeEnum = genAppDto.getCodeGenTypeEnum();
        ClassPathResource classPathResource = getClassPathResource(codeGenTypeEnum);
        return chatClient.prompt()
                .tools()
                .user(message)
                .advisors(advisorSpec -> {
                    advisorSpec.param(GEN_APP_INFO, new ChatContext(appId, userId));
                    advisorSpec.param(CONVERSATION_ID, appId);
                })
                .advisors(MessageChatMemoryAdvisor
                        .builder(MessageWindowChatMemory.builder()
                                .chatMemoryRepository(chatMemoryRepository)
                                .maxMessages(100)
                                .build())
                        .build())
                .system(classPathResource, UTF_8)
                .toolContext(Map.of(CONVERSATION_ID, appId, GEN_APP_INFO, new ToolsContext(appId, userId)))
                .stream().content();
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

    /**
     * 用来修复代码
     * @param genAppDto genAppDto
     * @return
     */
    public Flux<String> fixCode(GenAppDto genAppDto) {
        String userId = genAppDto.getUserLogin().getId();
        String appId = genAppDto.getAppId();
        return ChatClient.builder(openAiChatModel)
                .build().prompt()
                .user(genAppDto.getMessage())
                .system(new ClassPathResource("prompt/build-advisor-system-prompt.md"), UTF_8)
                .advisors(MessageChatMemoryAdvisor
                        .builder(MessageWindowChatMemory.builder()
                                .maxMessages(100)
                                .build())
                        .build())
                .tools(fileTools)
                .toolContext(Map.of(CONVERSATION_ID, appId, GEN_APP_INFO, new ToolsContext(appId, userId)))
                .stream().content();
    }


    /**
     * 根据提示词生成 CodeGenTypeEnum
     *
     * @param prompt 用户提示词
     * @param appId  appId
     * @param userId userId
     * @return CodeGenTypeEnum
     */
    public CodeGenTypeEnum selectGenTypeEnum(String prompt, Long appId, String userId) {
        String rawResponse = ChatClient.builder(openAiChatModel)
                .build().prompt()
                .user(prompt)
                .system(new ClassPathResource("prompt/codegen-routing-system-prompt.md"), UTF_8)
                .advisors(MessageChatMemoryAdvisor
                        .builder(MessageWindowChatMemory.builder()
                                .maxMessages(100)
                                .build())
                        .build())
                .advisors(advisorSpec -> {
                    advisorSpec.param(GEN_APP_INFO, new ChatContext(appId.toString(), userId));
                    advisorSpec.param(CONVERSATION_ID, appId);
                })
                .toolContext(Map.of(CONVERSATION_ID, appId.toString(), GEN_APP_INFO, new ToolsContext(appId.toString(), userId)))
                .call().content();
        ThinkingTagCleaner thinkingTagCleaner = new ThinkingTagCleaner();
        String normalizedResponse = normalizeEnumValue(thinkingTagCleaner.clean(rawResponse));
        return CodeGenTypeEnum.getEnumByValue(normalizedResponse);
    }

    /**
     * 异步生成应用名称
     *
     * @param initPrompt 用户初始提示词
     * @return 生成的应用名称
     */
    public String generateAppName(String initPrompt) {
        String rawResponse = ChatClient.builder(openAiChatModel)
                .build().prompt(initPrompt)
                .system(new ClassPathResource("prompt/app-naming-prompt.md"), UTF_8)
                .call().content();
        ThinkingTagCleaner thinkingTagCleaner = new ThinkingTagCleaner();
        return thinkingTagCleaner.clean(rawResponse);
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
