package com.leikooo.codemother.service.impl;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leikooo.codemother.ai.AiChatClient;
import com.leikooo.codemother.ai.GenerationManager;
import com.leikooo.codemother.ai.tools.TodolistTools;
import com.leikooo.codemother.constant.ResourcePathConstant;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.entity.AppVersion;
import com.leikooo.codemother.model.enums.*;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.AppVersionService;
import com.leikooo.codemother.service.ChatHistoryService;
import com.leikooo.codemother.service.SubAgentService;
import com.leikooo.codemother.utils.BuildOutputValidator;
import com.leikooo.codemother.utils.ProjectPathUtils;
import com.leikooo.codemother.utils.VueBuildUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * @author leikooo
 * @description SubAgent 自动修复服务实现
 * 实现 AI 修复 -> build -> validate -> 失败则重试 的循环
 */
@Slf4j
@Service
public class SubAgentServiceImpl implements SubAgentService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long AI_TIMEOUT_MINUTES = 30;
    private static final String METADATA_FILE = "metadata.json";
    private static final String BUG_PREFIX = "遇到了下面的 BUG: ";
    private static final int ERROR_SUMMARY_MAX_LENGTH = 200;
    private static final String UNKNOWN_ERROR = "未知错误";
    private static final String BUILD_VALIDATION_SEPARATOR = "\n\n=== Build Output Validation Failed ===\n";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiChatClient aiChatClient;
    private final AppVersionService appVersionService;
    private final GenerationManager generationManager;
    private final ChatHistoryService chatHistoryService;
    private final Executor fixExecutor;
    private final TodolistTools todolistTools;

    public SubAgentServiceImpl(
            AiChatClient aiChatClient,
            AppVersionService appVersionService,
            GenerationManager generationManager,
            ChatHistoryService chatHistoryService,
            @Qualifier("fixExecutor") Executor fixExecutor,
            TodolistTools todolistTools) {
        this.aiChatClient = aiChatClient;
        this.appVersionService = appVersionService;
        this.generationManager = generationManager;
        this.chatHistoryService = chatHistoryService;
        this.fixExecutor = fixExecutor;
        this.todolistTools = todolistTools;
    }

    private record AiFixResult(boolean success, String errorMessage) {
    }

    private record BuildValidationResult(boolean success, String errorLog) {
    }

    private record DoneEventPayload(boolean success, int totalAttempts, String summary, String aiContent) {
    }

    /**
     * 修复循环生命周期上下文，贯穿 doFixLoop 全链路
     */
    private static class FixLoopContext {
        private final FluxSink<ServerSentEvent<String>> sink;
        private final String appId;
        private final UserVO user;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final StringBuilder aiContent = new StringBuilder();

        FixLoopContext(FluxSink<ServerSentEvent<String>> sink, String appId, UserVO user) {
            this.sink = sink;
            this.appId = appId;
            this.user = user;
        }

        boolean isStopped() {
            return cancelled.get() || sink.isCancelled();
        }

        String aiContentAsString() {
            return aiContent.toString();
        }
    }

    /**
     * AI 流式修复的上下文，封装 sink 输出 + 异步同步状态
     */
    private static class FixStreamContext {
        private final FluxSink<ServerSentEvent<String>> sink;
        private final StringBuilder aiContent;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicBoolean hasError = new AtomicBoolean(false);
        private final AtomicReference<String> errorMessage = new AtomicReference<>();

        FixStreamContext(FixLoopContext loopContext) {
            this.sink = loopContext.sink;
            this.aiContent = loopContext.aiContent;
        }

        void markError(String message) {
            hasError.set(true);
            errorMessage.set(message);
            latch.countDown();
        }

        void markComplete() {
            latch.countDown();
        }

        AiFixResult toResult(boolean completedInTime) {
            if (!completedInTime) {
                return new AiFixResult(false, "AI 修复超时");
            }
            if (hasError.get()) {
                return new AiFixResult(false, errorMessage.get());
            }
            return new AiFixResult(true, null);
        }
    }

    @Override
    public Flux<ServerSentEvent<String>> executeFixLoop(String appId, UserVO user) {
        return Flux.create(sink -> {
            final FixLoopContext context = new FixLoopContext(sink, appId, user);
            CompletableFuture.runAsync(() -> {
                try {
                    doFixLoop(context);
                } catch (Exception e) {
                    log.error("[SubAgent] 修复循环异常: subAgentAppId={}", appId, e);
                    emitErrorAndComplete(context);
                }
            }, fixExecutor);
        }, FluxSink.OverflowStrategy.BUFFER);
    }


    /**
     * 修复主循环
     */
    private void doFixLoop(FixLoopContext context) {
        String errorMsg = appVersionService.getFixErrorMessage(Long.parseLong(context.appId));
        String lastErrorLog = "";

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (context.isStopped()) {
                emitCancelledAndComplete(context, attempt - 1);
                return;
            }
            final AiFixResult aiResult = streamAiFix(context, errorMsg, attempt);
            if (!aiResult.success()) {
                context.sink.next(buildResultEvent(false, attempt, aiResult.errorMessage()));
                continue;
            }
            final BuildValidationResult buildResult = buildAndValidate(context.sink, context.appId, attempt);
            lastErrorLog = buildResult.errorLog();
            if (buildResult.success()) {
                emitFixSuccess(context, attempt);
                return;
            }
            errorMsg = BUG_PREFIX + buildResult.errorLog();
            updateMetadataFields(context.appId, Map.of("errorLog", buildResult.errorLog()));
        }
        emitAllAttemptsExhausted(context, lastErrorLog);
    }

    /**
     * Phase 1: AI 流式修复
     */
    private AiFixResult streamAiFix(FixLoopContext context, String errorMsg, int attempt) {
        context.sink.next(phaseEvent(FixPhaseEnum.FIXING, attempt));
        final GenAppDto dto = new GenAppDto(errorMsg, context.appId, context.user);
        final FixStreamContext streamContext = new FixStreamContext(context);
        subscribeAiFixStream(streamContext, dto, attempt);
        final boolean completed = awaitWithTimeout(streamContext.latch);
        return streamContext.toResult(completed);
    }

    private void subscribeAiFixStream(FixStreamContext context, GenAppDto dto, int attempt) {
        aiChatClient.fixCode(dto)
                .doOnNext(chunk -> {
                    if (!context.sink.isCancelled()) {
                        context.aiContent.append(chunk);
                        context.sink.next(dataEvent(chunk));
                    }
                })
                .doOnError(e -> {
                    log.error("[SubAgent] AI 修复出错: appId={}, attempt={}", dto.getAppId(), attempt, e);
                    context.markError(e.getMessage());
                })
                .doOnComplete(context::markComplete)
                .subscribe();
    }

    private boolean awaitWithTimeout(CountDownLatch latch) {
        try {
            return latch.await(AI_TIMEOUT_MINUTES, MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Phase 2 + 3: 构建 & 校验
     */
    private BuildValidationResult buildAndValidate(FluxSink<ServerSentEvent<String>> sink,
                                                   String appId, int attempt) {
        sink.next(phaseEvent(FixPhaseEnum.BUILDING, attempt));
        final String projectPath = ProjectPathUtils.getProjectPath(appId);
        final VueBuildUtils.BuildResult buildResult = VueBuildUtils.buildVueProject(projectPath, appId);
        final BuildResultEnum buildResultEnum = BuildResultEnum.fromExitCode(buildResult.exitCode());
        if (buildResultEnum != BuildResultEnum.SUCCESS) {
            sink.next(buildResultEvent(false, attempt, buildResult.fullLog()));
            return new BuildValidationResult(false, buildResult.fullLog());
        }
        final BuildOutputValidator.ValidationResult validation =
                BuildOutputValidator.ValidationResult.validateVueBuild(projectPath);
        if (!validation.valid()) {
            final String errorLog = buildResult.fullLog() + BUILD_VALIDATION_SEPARATOR + validation.summary();
            sink.next(buildResultEvent(false, attempt, errorLog));
            return new BuildValidationResult(false, errorLog);
        }
        sink.next(buildResultEvent(true, attempt, "构建成功"));
        return new BuildValidationResult(true, "");
    }

    private void emitFixSuccess(FixLoopContext context, int attempt) {
        updateVersionStatus(context.appId, VersionStatusEnum.SUCCESS);
        final String summary = buildFixSummary(true, attempt, null);
        context.sink.next(doneEvent(new DoneEventPayload(true, attempt, summary, context.aiContentAsString())));
        context.sink.complete();
    }

    private void emitAllAttemptsExhausted(FixLoopContext context, String lastErrorLog) {
        final String summary = buildFixSummary(false, MAX_ATTEMPTS, lastErrorLog);
        chatHistoryService.addChatMessage(
                context.appId, summary,
                ChatHistoryMessageTypeEnum.AI.getValue(),
                context.user.getId()
        );
        context.sink.next(doneEvent(new DoneEventPayload(false, MAX_ATTEMPTS, summary, context.aiContentAsString())));
        context.sink.complete();
    }

    private void emitCancelledAndComplete(FixLoopContext context, int completedAttempts) {
        context.sink.next(doneEvent(new DoneEventPayload(false, completedAttempts, "用户取消", null)));
        context.sink.complete();
    }

    private void emitErrorAndComplete(FixLoopContext context) {
        if (context.sink.isCancelled()) {
            return;
        }
        context.sink.next(errorEvent(context.aiContentAsString()));
        context.sink.complete();
    }

    /**
     * 摘要构建
     */
    private String buildFixSummary(boolean success, int attempts, String lastError) {
        if (success) {
            return String.format("[自动修复完成] 经过 %d 次尝试，已成功修复构建错误并通过验证。", attempts);
        }
        final String truncatedError = truncateError(lastError);
        return String.format("[自动修复失败] 经过 %d 次尝试未能修复。最后错误：%s", attempts, truncatedError);
    }

    private String truncateError(String error) {
        if (error == null) {
            return UNKNOWN_ERROR;
        }
        if (error.length() <= ERROR_SUMMARY_MAX_LENGTH) {
            return error;
        }
        return error.substring(0, ERROR_SUMMARY_MAX_LENGTH) + "...";
    }

    /**
     * 版本 & Metadata 操作
     */
    private void updateVersionStatus(String appId, VersionStatusEnum status) {
        final Map<String, Object> updates = new HashMap<>();
        updates.put("status", status.name());
        if (status == VersionStatusEnum.SUCCESS) {
            updates.put("buildTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        updateMetadataFields(appId, updates);
        updateVersionStatusInDb(appId, status);
    }

    private void updateVersionStatusInDb(String appId, VersionStatusEnum status) {
        try {
            final Long appIdLong = Long.parseLong(appId);
            final Integer versionNum = appVersionService.getMaxVersionNum(appIdLong);
            appVersionService.lambdaUpdate()
                    .eq(AppVersion::getAppId, appIdLong)
                    .eq(AppVersion::getVersionNum, versionNum)
                    .set(AppVersion::getStatus, status.name())
                    .update();
            log.info("[SubAgent] 更新版本状态: appId={}, version=v{}, status={}", appId, versionNum, status);
        } catch (Exception e) {
            log.error("[SubAgent] 更新版本状态失败: appId={}", appId, e);
        }
    }

    private void updateMetadataFields(String appId, Map<String, Object> updates) {
        try {
            final Path metadataPath = resolveMetadataPath(appId);
            if (!Files.exists(metadataPath)) {
                return;
            }
            @SuppressWarnings("unchecked")
            final Map<String, Object> metadata = OBJECT_MAPPER.readValue(metadataPath.toFile(), Map.class);
            metadata.putAll(updates);
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(metadataPath.toFile(), metadata);
        } catch (Exception e) {
            log.error("[SubAgent] 更新 metadata 失败: appId={}", appId, e);
        }
    }

    private Path resolveMetadataPath(String appId) {
        final Long appIdLong = Long.parseLong(appId);
        final int versionNum = appVersionService.getMaxVersionNum(appIdLong);
        return Paths.get(ResourcePathConstant.GENERATED_APPS_DIR, appId, "v" + versionNum)
                .resolve(METADATA_FILE);
    }

    /**
     * SSE 事件构建
     */
    private ServerSentEvent<String> phaseEvent(FixPhaseEnum phase, int attempt) {
        return ServerSentEvent.<String>builder()
                .event(SseEventTypeEnum.PHASE.getValue())
                .data(JSONUtil.toJsonStr(Map.of("phase", phase.getValue(), "attempt", attempt)))
                .build();
    }

    private ServerSentEvent<String> dataEvent(String chunk) {
        return ServerSentEvent.<String>builder()
                .data(JSONUtil.toJsonStr(Map.of("d", chunk)))
                .build();
    }

    private ServerSentEvent<String> buildResultEvent(boolean success, int attempt, String logMsg) {
        return ServerSentEvent.<String>builder()
                .event(SseEventTypeEnum.BUILD_RESULT.getValue())
                .data(JSONUtil.toJsonStr(Map.of(
                        "success", success,
                        "attempt", attempt,
                        "log", logMsg != null ? logMsg : "")))
                .build();
    }

    private ServerSentEvent<String> doneEvent(DoneEventPayload payload) {
        final Map<String, Object> data = new HashMap<>();
        data.put("success", payload.success());
        data.put("totalAttempts", payload.totalAttempts());
        if (payload.summary() != null) {
            data.put("summary", payload.summary());
        }
        if (payload.aiContent() != null) {
            data.put("aiContent", payload.aiContent());
        }
        return ServerSentEvent.<String>builder()
                .event(SseEventTypeEnum.DONE.getValue())
                .data(JSONUtil.toJsonStr(data))
                .build();
    }

    private ServerSentEvent<String> errorEvent(String message) {
        return ServerSentEvent.<String>builder()
                .event(SseEventTypeEnum.ERROR.getValue())
                .data(JSONUtil.toJsonStr(Map.of("error", message)))
                .build();
    }
}
