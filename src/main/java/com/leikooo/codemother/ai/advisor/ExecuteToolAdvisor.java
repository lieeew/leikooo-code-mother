package com.leikooo.codemother.ai.advisor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.leikooo.codemother.exception.BusinessException;
import com.leikooo.codemother.exception.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 手动执行 tool 的 StreamAdvisor：关闭框架内部执行，自行执行并可在工具返回值中注入提醒（如更新 todo）。
 *
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/3/14
 */
@Slf4j
@Component
public class ExecuteToolAdvisor implements StreamAdvisor {

    private static final String TODO_REMINDER = "<reminder>Update your todos.</reminder>";
    private static final String JSON_ERROR_MESSAGE =
            "Tool call JSON parse failed. Fix and retry.\n"
                    + "Rules: strict RFC8259 JSON, no trailing commas, no comments, "
                    + "no unescaped control chars in strings (escape newlines as \\n, tabs as \\t), "
                    + "all keys double-quoted.";
    private static final int MAX_TOOL_RETRY = 3;
    private static final int ORDER = Integer.MAX_VALUE - 100;
    private static final String TODO_METHOD = "todoUpdate";
    private static final int REMINDER_THRESHOLD = 3;

    /**
     * 三次工具没有使用 todoTool 那么就在 tool_result[0] 位置添加 TODO_REMINDER
     */
    private final Cache<String, Integer> roundsSinceTodo = Caffeine.newBuilder()
            .maximumSize(10_00)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();
    @Resource
    private ToolCallingManager toolCallingManager;

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        Assert.notNull(streamAdvisorChain, "streamAdvisorChain must not be null");
        Assert.notNull(chatClientRequest, "chatClientRequest must not be null");

        if (chatClientRequest.prompt().getOptions() == null
                || !(chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions)) {
            throw new IllegalArgumentException(
                    "ExecuteToolAdvisor requires ToolCallingChatOptions to be set in the ChatClientRequest options.");
        }

        var optionsCopy = (ToolCallingChatOptions) chatClientRequest.prompt().getOptions().copy();
        optionsCopy.setInternalToolExecutionEnabled(false);

        return internalStream(streamAdvisorChain, chatClientRequest, optionsCopy,
                chatClientRequest.prompt().getInstructions(), 0);
    }

    private Flux<ChatClientResponse> internalStream(
            StreamAdvisorChain streamAdvisorChain,
            ChatClientRequest originalRequest,
            ToolCallingChatOptions optionsCopy,
            List<Message> instructions,
            int jsonRetryCount) {
        return Flux.deferContextual(contextView -> {
            var processedRequest = ChatClientRequest.builder()
                    .prompt(new Prompt(instructions, optionsCopy))
                    .context(originalRequest.context())
                    .build();

            StreamAdvisorChain chainCopy = streamAdvisorChain.copy(this);
            Flux<ChatClientResponse> responseFlux = chainCopy.nextStream(processedRequest);

            AtomicReference<ChatClientResponse> aggregatedResponseRef = new AtomicReference<>();
            AtomicReference<List<ChatClientResponse>> chunksRef = new AtomicReference<>(new ArrayList<>());

            return new ChatClientMessageAggregator()
                    .aggregateChatClientResponse(responseFlux, aggregatedResponseRef::set)
                    .doOnNext(chunk -> chunksRef.get().add(chunk))
                    .ignoreElements()
                    .cast(ChatClientResponse.class)
                    .concatWith(Flux.defer(() -> processAggregatedResponse(
                            aggregatedResponseRef.get(), chunksRef.get(), processedRequest,
                            streamAdvisorChain, originalRequest, optionsCopy, jsonRetryCount)));
        });
    }

    private Flux<ChatClientResponse> processAggregatedResponse(
            ChatClientResponse aggregatedResponse,
            List<ChatClientResponse> chunks,
            ChatClientRequest finalRequest,
            StreamAdvisorChain streamAdvisorChain,
            ChatClientRequest originalRequest,
            ToolCallingChatOptions optionsCopy,
            int retryCount) {
        if (aggregatedResponse == null) {
            return Flux.fromIterable(chunks);
        }

        ChatResponse chatResponse = aggregatedResponse.chatResponse();
        boolean isToolCall = chatResponse != null && chatResponse.hasToolCalls();

        if (isToolCall) {
            Assert.notNull(chatResponse, "chatResponse must not be null when hasToolCalls is true");
            ChatClientResponse finalAggregatedResponse = aggregatedResponse;

            Flux<ChatClientResponse> toolCallFlux = Flux.deferContextual(ctx -> {
                ToolExecutionResult toolExecutionResult;
                try {
                    ToolCallReactiveContextHolder.setContext(ctx);
                    toolExecutionResult = toolCallingManager.executeToolCalls(finalRequest.prompt(), chatResponse);
                } catch (Exception e) {
                    if (retryCount < MAX_TOOL_RETRY) {
                        List<Message> retryInstructions = buildRetryInstructions(finalRequest, chatResponse, e);
                        if (retryInstructions != null) {
                            return internalStream(streamAdvisorChain, originalRequest, optionsCopy,
                                    retryInstructions, retryCount + 1);
                        }
                    }
                    throw e;
                } finally {
                    ToolCallReactiveContextHolder.clearContext();
                }
                List<Message> historyWithReminder = injectReminderIntoConversationHistory(
                        toolExecutionResult.conversationHistory(), getAppId(finalRequest));

                if (toolExecutionResult.returnDirect()) {
                    return Flux.just(buildReturnDirectResponse(finalAggregatedResponse, chatResponse,
                            toolExecutionResult, historyWithReminder));
                }
                return internalStream(streamAdvisorChain, originalRequest, optionsCopy, historyWithReminder, 0);
            });
            return toolCallFlux.subscribeOn(Schedulers.boundedElastic());
        }

        return Flux.fromIterable(chunks);
    }

    /**
     * 获取 AppId
     */
    private String getAppId(ChatClientRequest finalRequest) {
        if (finalRequest.prompt().getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
            return toolCallingChatOptions.getToolContext().get(CONVERSATION_ID).toString();
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR);
    }

    private static List<Message> buildRetryInstructions(ChatClientRequest finalRequest,
                                                        ChatResponse chatResponse,
                                                        Throwable error) {
        AssistantMessage assistantMessage = extractAssistantMessage(chatResponse);
        if (assistantMessage == null || assistantMessage.getToolCalls() == null
                || assistantMessage.getToolCalls().isEmpty()) {
            return null;
        }
        List<Message> instructions = new ArrayList<>(finalRequest.prompt().getInstructions());
        instructions.add(assistantMessage);

        String errorMessage = buildJsonErrorMessage(error);
        List<ToolResponseMessage.ToolResponse> responses = assistantMessage.getToolCalls().stream()
                .map(toolCall -> new ToolResponseMessage.ToolResponse(
                        toolCall.id(),
                        toolCall.name(),
                        errorMessage))
                .toList();
        instructions.add(ToolResponseMessage.builder().responses(responses).build());
        return instructions;
    }

    private static AssistantMessage extractAssistantMessage(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }
        Generation result = chatResponse.getResult();
        if (result != null && result.getOutput() != null) {
            return result.getOutput();
        }
        List<Generation> results = chatResponse.getResults();
        if (results != null && !results.isEmpty() && results.get(0).getOutput() != null) {
            return results.get(0).getOutput();
        }
        return null;
    }

    private static String buildJsonErrorMessage(Throwable error) {
        String detail = ExceptionUtils.getRootCauseMessage(error);
        if (detail.isBlank()) {
            return JSON_ERROR_MESSAGE;
        }
        return JSON_ERROR_MESSAGE + "\nError: " + detail;
    }

    /**
     * 对 conversationHistory 中的 TOOL 类消息，在其每个 ToolResponse 的 responseData 后追加提醒。
     */
    private List<Message> injectReminderIntoConversationHistory(List<Message> conversationHistory, String appId) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return conversationHistory;
        }
        if (!(conversationHistory.getLast() instanceof ToolResponseMessage toolMsg)) {
            return conversationHistory;
        }
        List<ToolResponseMessage.ToolResponse> responses = toolMsg.getResponses();
        if (responses.isEmpty()) {
            return conversationHistory;
        }
        ToolResponseMessage.ToolResponse firstResponse = responses.getFirst();
        if (!updateRoundsAndCheckReminder(appId, firstResponse.name())) {
            return conversationHistory;
        }
        List<ToolResponseMessage.ToolResponse> newResponses = new ArrayList<>(responses);
        ToolResponseMessage.ToolResponse actualRes = newResponses.removeFirst();
        newResponses.add(new ToolResponseMessage.ToolResponse(
                firstResponse.id(), "text", TODO_REMINDER));
        newResponses.add(actualRes);
        List<Message> result = new ArrayList<>(
                conversationHistory.subList(0, conversationHistory.size() - 1));
        result.add(ToolResponseMessage.builder().responses(newResponses).build());
        return result;
    }

    /**
     * 构造 returnDirect 时的 ChatClientResponse，使用注入提醒后的 conversationHistory 生成 generations。
     */
    private static ChatClientResponse buildReturnDirectResponse(
            ChatClientResponse aggregatedResponse,
            ChatResponse chatResponse,
            ToolExecutionResult originalResult,
            List<Message> historyWithReminder) {
        ToolExecutionResult resultWithReminder = ToolExecutionResult.builder()
                .conversationHistory(historyWithReminder)
                .returnDirect(originalResult.returnDirect())
                .build();
        ChatResponse newChatResponse = ChatResponse.builder()
                .from(chatResponse)
                .generations(ToolExecutionResult.buildGenerations(resultWithReminder))
                .build();
        return aggregatedResponse.mutate().chatResponse(newChatResponse).build();
    }

    /**
     * updateRoundsAndCheckReminder
     * @param appId appId
     * @param methodName methodName
     * @return 是否需要更新
     */
    private boolean updateRoundsAndCheckReminder(String appId, String methodName) {
        if (TODO_METHOD.equals(methodName)) {
            roundsSinceTodo.put(appId, 0);
            return false;
        }
        int count = roundsSinceTodo.asMap().merge(appId, 1, Integer::sum);
        return count >= REMINDER_THRESHOLD;
    }


    @Override
    public String getName() {
        return "ExecuteToolAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}



