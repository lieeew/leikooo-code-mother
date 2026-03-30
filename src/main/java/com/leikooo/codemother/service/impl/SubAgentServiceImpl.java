package com.leikooo.codemother.service.impl;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.leikooo.codemother.ai.AiChatClient;
import com.leikooo.codemother.constant.ResourcePathConstant;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.enums.BuildResultEnum;
import com.leikooo.codemother.model.enums.FixPhaseEnum;
import com.leikooo.codemother.model.enums.SseEventTypeEnum;
import com.leikooo.codemother.model.enums.VersionStatusEnum;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.AppVersionService;
import com.leikooo.codemother.service.SubAgentService;
import com.leikooo.codemother.utils.BuildOutputValidator;
import com.leikooo.codemother.utils.ProjectPathUtils;
import com.leikooo.codemother.utils.UuidV7Generator;
import com.leikooo.codemother.utils.VueBuildUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

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
    private static final String BUILD_VALIDATION_SEPARATOR = "\n\n=== Build Output Validation Failed ===\n";
    private static final String NO_ERROR_LOG = "报错日志不存在";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiChatClient aiChatClient;
    private final AppVersionService appVersionService;
    private final ChatMemoryRepository subAgentChatMemoryRepository;
    private final String ERROR_LOG = "errorLog";

    public SubAgentServiceImpl(
            AiChatClient aiChatClient,
            AppVersionService appVersionService,
            @Qualifier("subAgentChatMemoryRepository")
            ChatMemoryRepository subAgentChatMemoryRepository) {
        this.aiChatClient = aiChatClient;
        this.appVersionService = appVersionService;
        this.subAgentChatMemoryRepository = subAgentChatMemoryRepository;
    }

    private record BuildValidationResult(boolean success, String errorLog) {
    }

    private record DoneEventPayload(boolean success, int totalAttempts, String summary, String aiContent) {
    }

    /**
     * 修复循环生命周期上下文，贯穿 doFixLoop 全链路
     */
    private static class FixLoopContext {
        private final String appId;
        private final Integer targetVersionNum;
        private final UserVO user;
        private final String conversationId;
        private final StringBuilder aiContent = new StringBuilder();

        FixLoopContext(String appId, Integer targetVersionNum, UserVO user, String conversationId) {
            this.appId = appId;
            this.targetVersionNum = targetVersionNum;
            this.user = user;
            this.conversationId = conversationId;
        }

        String aiContentAsString() {
            return aiContent.toString();
        }
    }

    private Flux<ServerSentEvent<String>> streamAiFixReactive(FixLoopContext context, int attempt) {
        return Mono.fromCallable(() -> getErrorLogs(context))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(errorLog -> {
                    final GenAppDto dto = new GenAppDto(errorLog, context.appId, context.user, context.conversationId);
                    return Flux.concat(
                            Flux.just(phaseEvent(FixPhaseEnum.FIXING, attempt)),
                            aiChatClient.fixCode(dto)
                                    .timeout(Duration.ofMinutes(AI_TIMEOUT_MINUTES))
                                    .doOnNext(context.aiContent::append)
                                    .map(this::dataEvent));
                });
    }

    private Flux<ServerSentEvent<String>> doFixLoopReactive(FixLoopContext context, int attempt) {
        if (attempt > MAX_ATTEMPTS) {
            return Flux.just(doneEvent(new DoneEventPayload(false, MAX_ATTEMPTS, "", context.aiContentAsString())));
        }
        return Flux.concat(streamAiFixReactive(context, attempt), continueAfterAi(context, attempt))
                .onErrorResume(e -> handleAiFailure(context, attempt, e));
    }

    @Override
    public Flux<ServerSentEvent<String>> executeFixLoop(String appId, UserVO user) {
        final Long appIdLong = Long.parseLong(appId);
        return Mono.fromCallable(() -> appVersionService.getCurrentVersionNum(appIdLong))
                .subscribeOn(Schedulers.boundedElastic())
                .defaultIfEmpty(0)
                .flatMapMany(currentVersionNum -> {
                    if (currentVersionNum == null || currentVersionNum <= 0) {
                        return missingVersionEvents();
                    }
                    final FixLoopContext context =
                            new FixLoopContext(appId, currentVersionNum, user, generateSubAgentConversationId(appId));
                    return doFixLoopReactive(context, 1)
                            .doFinally(signalType -> cleanupConversationMemory(context));
                });
    }

    private String generateSubAgentConversationId(String appId) {
        return "subfix:" + appId + ":" + UuidV7Generator.bytesToUuid(UuidV7Generator.generate());
    }

    private void cleanupConversationMemory(FixLoopContext context) {
        try {
            subAgentChatMemoryRepository.deleteByConversationId(context.conversationId);
            log.info("[SubAgent] Cleared chat memory, appId: {}, conversationId: {}",
                    context.appId, context.conversationId);
        } catch (Exception e) {
            log.warn("[SubAgent] Failed to clear chat memory, appId: {}, conversationId: {}",
                    context.appId, context.conversationId, e);
        }
    }

    private Flux<ServerSentEvent<String>> continueAfterAi(FixLoopContext context, int attempt) {
        return Mono.fromCallable(() -> {
                    final BuildValidationResult buildResult = buildAndValidate(context.appId);
                    updateMetadataFile(context, buildResult);
                    syncVersionStatus(context, buildResult);
                    return buildResult;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(buildResult -> afterBuild(context, attempt, buildResult));
    }

    private Flux<ServerSentEvent<String>> missingVersionEvents() {
        return Flux.just(
                errorEvent("当前版本不存在，无法执行修复"),
                doneEvent(new DoneEventPayload(false, 0, "当前版本不存在，无法执行修复", ""))
        );
    }

    private Flux<ServerSentEvent<String>> afterBuild(FixLoopContext context, int attempt,
                                                     BuildValidationResult buildResult) {
        final Flux<ServerSentEvent<String>> buildEvents = buildEvents(buildResult, attempt);
        if (buildResult.success()) {
            return Flux.concat(buildEvents, Flux.just(doneEvent(successPayload(context, attempt))));
        }
        if (attempt >= MAX_ATTEMPTS) {
            return Flux.concat(buildEvents, Flux.just(doneEvent(failurePayload(context, attempt))));
        }
        return Flux.concat(buildEvents, doFixLoopReactive(context, attempt + 1));
    }

    private Flux<ServerSentEvent<String>> buildEvents(BuildValidationResult buildResult, int attempt) {
        return Flux.just(
                phaseEvent(FixPhaseEnum.BUILDING, attempt),
                buildResultEvent(buildResult.success(), attempt, buildLog(buildResult))
        );
    }

    private DoneEventPayload successPayload(FixLoopContext context, int attempt) {
        return new DoneEventPayload(true, attempt, "成功", context.aiContentAsString());
    }

    private DoneEventPayload failurePayload(FixLoopContext context, int attempt) {
        return new DoneEventPayload(false, attempt, "", context.aiContentAsString());
    }

    private DoneEventPayload aiFailurePayload(FixLoopContext context, int attempt, String message) {
        return new DoneEventPayload(false, attempt, message, context.aiContentAsString());
    }

    private String buildLog(BuildValidationResult buildResult) {
        if (buildResult.success()) {
            return "构建成功";
        }
        return buildResult.errorLog();
    }

    /**
     * 构建 & 校验
     */
    private BuildValidationResult buildAndValidate(String appId) {
        final String projectPath = ProjectPathUtils.getProjectPath(appId);
        final VueBuildUtils.BuildResult buildResult = VueBuildUtils.buildVueProject(projectPath, appId);
        final BuildResultEnum buildResultEnum = BuildResultEnum.fromExitCode(buildResult.exitCode());
        if (buildResultEnum != BuildResultEnum.SUCCESS) {
            return new BuildValidationResult(false, buildResult.fullLog());
        }
        final BuildOutputValidator.ValidationResult validation =
                BuildOutputValidator.ValidationResult.validateVueBuild(projectPath);
        if (!validation.valid()) {
            final String errorLog = buildResult.fullLog() + BUILD_VALIDATION_SEPARATOR + validation.summary();
            return new BuildValidationResult(false, errorLog);
        }
        return new BuildValidationResult(true, "");
    }

    /**
     * 版本 & Metadata 操作
     */
    private void updateMetadataFile(FixLoopContext context, BuildValidationResult buildResult) {
        final File file = resolveMetadataPath(context).toFile();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = OBJECT_MAPPER.readValue(file, Map.class);
            metadata.put("status", buildResult.success() ? VersionStatusEnum.SUCCESS : VersionStatusEnum.NEED_FIX);
            metadata.put("buildTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            if (!buildResult.success()) {
                metadata.put(ERROR_LOG, buildResult.errorLog());
            } else {
                metadata.remove(ERROR_LOG);
            }
            ObjectWriter writer = OBJECT_MAPPER.writerWithDefaultPrettyPrinter();
            writer.writeValue(file, metadata);
        } catch (IOException e) {
            log.error("更新 metadata.json 失败, appId: {}, version: {}", context.appId, context.targetVersionNum, e);
        }
    }

    private void syncVersionStatus(FixLoopContext context, BuildValidationResult buildResult) {
        VersionStatusEnum status = buildResult.success() ? VersionStatusEnum.SUCCESS : VersionStatusEnum.NEED_FIX;
        appVersionService.updateVersionStatus(context.appId, context.targetVersionNum, status);
    }

    /**
     * 获取 error 日志
     * @return 日志
     */
    private String getErrorLogs(FixLoopContext context) {
        final Path metadataPath = resolveMetadataPath(context);
        if (!Files.exists(metadataPath)) {
            return NO_ERROR_LOG;
        }
        try {
            @SuppressWarnings("unchecked") final Map<String, Object> metadata = OBJECT_MAPPER.readValue(metadataPath.toFile(), Map.class);
            Object errorLog = metadata.get(ERROR_LOG);
            if (errorLog == null) {
                return NO_ERROR_LOG;
            }
            return errorLog.toString();
        } catch (IOException e) {
            log.warn("读取 metadata.json 失败, appId: {}, version: {}", context.appId, context.targetVersionNum, e);
            return NO_ERROR_LOG;
        }
    }

    private Path resolveMetadataPath(FixLoopContext context) {
        return Paths.get(ResourcePathConstant.GENERATED_APPS_DIR, context.appId, "v" + context.targetVersionNum)
                .resolve(METADATA_FILE);
    }

    private Flux<ServerSentEvent<String>> handleAiFailure(FixLoopContext context, int attempt, Throwable error) {
        String message = toAiErrorMessage(error);
        log.error("[SubAgent] AI 修复失败, appId: {}, attempt: {}, error: {}",
                context.appId, attempt, message, error);
        return Flux.just(
                errorEvent(message),
                doneEvent(aiFailurePayload(context, attempt, message))
        );
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

    private String toAiErrorMessage(Throwable error) {
        if (error instanceof TimeoutException) {
            return "AI 修复超时";
        }
        if (error.getCause() instanceof TimeoutException) {
            return "AI 修复超时";
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "[SubAgent] AI 修复失败";
        }
        return message;
    }
}
