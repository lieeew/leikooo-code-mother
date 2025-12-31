package com.leikooo.codemother.ai;

import com.leikooo.codemother.ai.model.HtmlCodeResult;
import com.leikooo.codemother.ai.tools.FileTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/29
 * @description
 */
@Slf4j
@SpringBootTest
@Profile("local")
class AiCodeGeneratorServiceTest {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Resource
    private FileTools fileTools;

    @Test
    void generateCode() {
        AiCodeGeneratorService aiCodeGeneratorService = AiServices.create(AiCodeGeneratorService.class, chatModel);
        HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateCode("生成一个在线学习编程网站");
        String describe = htmlCodeResult.getDescribe();
        String code = htmlCodeResult.getCode();
        System.out.println("code = " + code);
        System.out.println("describe = " + describe);
    }

    @Test
    void generateCodeStream() throws InterruptedException {
        AiCodeGeneratorService aiCodeGeneratorService = AiServices
                .builder(AiCodeGeneratorService.class)
                .tools(fileTools)
                .chatMemory(new MessageWindowChatMemory.Builder()
                        .chatMemoryStore(new InMemoryChatMemoryStore())
                        .maxMessages(100)
                        .build())
                .streamingChatModel(streamingChatModel)
                .build();
        StringBuilder sb = new StringBuilder();
        TokenStream generateCodeStream = aiCodeGeneratorService.generateCodeStream("生成一个在线学习 Spring 面试题目的网站");

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        generateCodeStream
                .onPartialResponse((String partialResponse) -> System.out.println(partialResponse))
                .onPartialThinking((PartialThinking partialThinking) -> System.out.println(partialThinking))
                .onRetrieved((List<Content> contents) -> System.out.println(contents))
                .onIntermediateResponse((ChatResponse intermediateResponse) -> System.out.println(intermediateResponse))
                // This will be invoked right before a tool is executed. BeforeToolExecution contains ToolExecutionRequest (e.g. tool name, tool arguments, etc.)
                .beforeToolExecution((BeforeToolExecution beforeToolExecution) -> System.out.println(beforeToolExecution))
                // This will be invoked right after a tool is executed. ToolExecution contains ToolExecutionRequest and tool execution result.
                .onToolExecuted((ToolExecution toolExecution) -> System.out.println(toolExecution))
                .onCompleteResponse((ChatResponse response) -> futureResponse.complete(response))
                .onError((Throwable error) -> futureResponse.completeExceptionally(error))
                .start();

        futureResponse.join(); // Blocks the main thread until the streaming process (running in another thread) is complete
    }
}