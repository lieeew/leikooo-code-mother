package com.leikooo.codemother.controller;

import com.leikooo.codemother.ai.AiChatClient;
import com.leikooo.codemother.ai.advisor.ToolAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
public class ChatController {

    private final OpenAiChatModel chatModel;
    private final AiChatClient aiChatClient;
    private final ChatClient chatClient;

    public ChatController(OpenAiChatModel chatModel, AiChatClient aiChatClient) {
        this.chatModel = chatModel;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.aiChatClient = aiChatClient;
    }


    @GetMapping("/ai/generate")
    public Map<String, String> generate(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        return Map.of("generation", this.aiChatClient.generateCode(message));
    }

    @GetMapping("/ai/generateStream")
    public Flux<ChatClientResponse> generateStream(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
//        Prompt prompt = new Prompt(new UserMessage(message));
//        Flux<ChatResponse> stream = this.chatModel.stream(prompt);
//        Flux<ChatResponse> chatResponseFlux =
//                stream.doOnNext(subscription -> {
//                    System.out.println("subscription = " + subscription);
//                }).doFinally(signalType -> {
//                    System.out.println("signalType = " + signalType);
//                });
        Flux<ChatClientResponse> call = chatClient.prompt("给我讲一个故事")
                .advisors(new ToolAdvisor())
                .stream().chatClientResponse();
        return call.doOnNext(response -> {
            System.out.println("message = " + response);
        }).doFinally(signalType -> {
            System.out.println("signalType = " + signalType);
        });
    }
}