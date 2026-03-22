package com.leikooo.codemother.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.leikooo.codemother.ai.AiChatClient;
import com.leikooo.codemother.ai.GenerationManager;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.enums.BuildResultEnum;
import com.leikooo.codemother.model.enums.ChatHistoryMessageTypeEnum;
import com.leikooo.codemother.model.enums.ChatHistoryMessageTypeEnum;
import com.leikooo.codemother.model.enums.VersionStatusEnum;
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.leikooo.codemother.model.enums.ChatHistoryMessageTypeEnum.AI;
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

    private final AiChatClient aiChatClient;
    private final AppVersionService appVersionService;
    private final GenerationManager generationManager;
    private final ChatHistoryService chatHistoryService;
    private final Executor fixExecutor;

    public SubAgentServiceImpl(
            AiChatClient aiChatClient,
            AppVersionService appVersionService,
            GenerationManager generationManager,
            ChatHistoryService chatHistoryService,
            @Qualifier("fixExecutor") Executor fixExecutor) {
        this.aiChatClient = aiChatClient;
        this.appVersionService = appVersionService;
        this.generationManager = generationManager;
        this.chatHistoryService = chatHistoryService;
        this.fixExecutor = fixExecutor;
    }

    @Override
    public Flux<ServerSentEvent<String>> executeFixLoop(String appId, UserVO user) {
        // 生成唯一的 SubAgent key
        final String subAgentId = generationManager.generateSubAgentKey(appId);

        return Flux.create(sink -> {
            // 注册取消能力
            AtomicBoolean cancelled = new AtomicBoolean(false);
            sink.onCancel(() -> {
                log.info("[SubAgent] SSE 连接取消: subAgentId={}", subAgentId);
                cancelled.set(true);
            });
            sink.onDispose(() -> {
                log.info("[SubAgent] SSE 连接释放: subAgentId={}", subAgentId);
                cancelled.set(true);
            });
            generationManager.register(subAgentId, () -> cancelled.set(true));

            // 后台线程驱动循环
            CompletableFuture.runAsync(() -> {
                try {
                    doFixLoop(sink, appId, subAgentId, user, cancelled);
                } catch (Exception e) {
                    log.error("[SubAgent] 修复循环异常: subAgentId={}", subAgentId, e);
                    if (!sink.isCancelled()) {
                        sink.next(errorEvent(e.getMessage()));
                        sink.complete();
                    }
                } finally {
                    generationManager.cancel(subAgentId);
                }
            }, fixExecutor);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private void doFixLoop(FluxSink<ServerSentEvent<String>> sink,
                           String appId, String subAgentId, UserVO user,
                           AtomicBoolean cancelled) {
        // 首次从 metadata 获取错误信息
        String errorMsg = appVersionService.getFixErrorMessage(Long.parseLong(appId));

        StringBuilder aiContent = new StringBuilder();
        String lastErrorLog = "";

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (cancelled.get() || sink.isCancelled()) {
                sink.next(doneEvent(false, attempt - 1, "用户取消", null));
                sink.complete();
                return;
            }

            // 捕获当前 attempt 值供 lambda 使用
            final int currentAttempt = attempt;

            // ── Phase 1: AI 修复（流式） ──
            sink.next(phaseEvent("fixing", currentAttempt));

            // 使用唯一的 subAgentId 作为 conversationId
            GenAppDto dto = new GenAppDto(errorMsg, appId, user);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean aiError = new AtomicBoolean(false);
            AtomicReference<String> errorHolder = new AtomicReference<>();

            aiChatClient.fixCode(dto)
                    .doOnNext(chunk -> {
                        log.info("error chuck = {}", chunk);
                        if (!sink.isCancelled()) {
                            aiContent.append(chunk);
                            sink.next(dataEvent(chunk));
                        }
                    })
                    .doOnError(e -> {
                        log.error("[SubAgent] AI 修复出错: appId={}, attempt={}", appId, currentAttempt, e);
                        aiError.set(true);
                        errorHolder.set(e.getMessage());
                        latch.countDown();
                    })
                    .doOnComplete(latch::countDown)
                    .subscribe();

            // 阻塞等待 AI 流式输出完成（带超时保护）
            boolean completed;
            try {
                completed = latch.await(AI_TIMEOUT_MINUTES, MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                completed = false;
            }

            if (!completed || aiError.get()) {
                String errMsg = !completed ? "AI 修复超时" : errorHolder.get();
                log.warn("[SubAgent] AI 修复失败: appId={}, attempt={}, reason={}", appId, currentAttempt, errMsg);
                sink.next(buildResultEvent(false, currentAttempt, errMsg));
                continue;
            }

            // ── Phase 2: 构建 ──
            sink.next(phaseEvent("building", currentAttempt));

            String projectPath = ProjectPathUtils.getProjectPath(appId);
            VueBuildUtils.BuildResult buildResult = VueBuildUtils.buildVueProject(projectPath, appId);
            BuildResultEnum buildResultEnum = BuildResultEnum.fromExitCode(buildResult.exitCode());

            // ── Phase 3: 校验 ──
            boolean success = false;
            String newErrorLog = "";

            if (buildResultEnum == BuildResultEnum.SUCCESS) {
                BuildOutputValidator.ValidationResult validation =
                        BuildOutputValidator.ValidationResult.validateVueBuild(projectPath);
                if (validation.valid()) {
                    success = true;
                } else {
                    newErrorLog = buildResult.fullLog() + "\n\n=== Build Output Validation Failed ===\n" + validation.summary();
                }
            } else {
                newErrorLog = buildResult.fullLog();
            }

            sink.next(buildResultEvent(success, currentAttempt, success ? "构建成功" : newErrorLog));
            lastErrorLog = newErrorLog;

            // ── 结果判定 ──
            if (success) {
                // 更新版本状态为 SUCCESS
                updateVersionStatus(appId, VersionStatusEnum.SUCCESS);
                String summary = buildFixSummary(true, currentAttempt, aiContent.toString(), null);
                sink.next(doneEvent(true, currentAttempt, summary, aiContent.toString()));
                sink.complete();
                return;
            }

            // 用新的构建错误直接作为下次 AI 输入
            errorMsg = "遇到了下面的 BUG: " + newErrorLog;
            // 同时持久化到 metadata
            updateMetadataErrorLog(appId, newErrorLog);
        }

        // 所有尝试耗尽
        String summary = buildFixSummary(false, MAX_ATTEMPTS, aiContent.toString(), lastErrorLog);
        chatHistoryService.addChatMessage(
                appId, summary,
                ChatHistoryMessageTypeEnum.AI.getValue(),
                user.getId()
        );
        sink.next(doneEvent(false, MAX_ATTEMPTS, summary, aiContent.toString()));
        sink.complete();
    }

    private String buildFixSummary(boolean success, int attempts, String aiContent, String lastError) {
        if (success) {
            return String.format(
                    "[自动修复完成] 经过 %d 次尝试，已成功修复构建错误并通过验证。",
                    attempts);
        } else {
            // 截取关键错误信息，避免摘要过长
            String truncatedError = lastError != null && lastError.length() > 200
                    ? lastError.substring(0, 200) + "..."
                    : (lastError != null ? lastError : "未知错误");
            return String.format(
                    "[自动修复失败] 经过 %d 次尝试未能修复。最后错误：%s",
                    attempts, truncatedError);
        }
    }

    private void updateVersionStatus(String appId, VersionStatusEnum status) {
        try {
            Long appIdLong = Long.parseLong(appId);
            Integer versionNum = appVersionService.getMaxVersionNum(appIdLong);

            Path versionPath = Paths.get(
                    com.leikooo.codemother.constant.ResourcePathConstant.GENERATED_APPS_DIR,
                    appId, "v" + versionNum);
            Path metadataPath = versionPath.resolve("metadata.json");

            if (java.nio.file.Files.exists(metadataPath)) {
                com.fasterxml.jackson.databind.ObjectMapper objectMapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
                metadata.put("status", status.name());
                if (status == VersionStatusEnum.SUCCESS) {
                    metadata.put("buildTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataPath.toFile(), metadata);
            }

            // 更新数据库
            appVersionService.lambdaUpdate()
                    .eq(com.leikooo.codemother.model.entity.AppVersion::getAppId, appIdLong)
                    .eq(com.leikooo.codemother.model.entity.AppVersion::getVersionNum, versionNum)
                    .set(com.leikooo.codemother.model.entity.AppVersion::getStatus, status.name())
                    .update();

            log.info("[SubAgent] 更新版本状态: appId={}, version=v{}, status={}", appId, versionNum, status);
        } catch (Exception e) {
            log.error("[SubAgent] 更新版本状态失败: appId={}", appId, e);
        }
    }

    private void updateMetadataErrorLog(String appId, String errorLog) {
        try {
            Long appIdLong = Long.parseLong(appId);
            int versionNum = appVersionService.getMaxVersionNum(appIdLong);

            Path versionPath = Paths.get(
                    com.leikooo.codemother.constant.ResourcePathConstant.GENERATED_APPS_DIR,
                    appId, "v" + versionNum);
            Path metadataPath = versionPath.resolve("metadata.json");

            if (java.nio.file.Files.exists(metadataPath)) {
                com.fasterxml.jackson.databind.ObjectMapper objectMapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
                metadata.put("errorLog", errorLog);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataPath.toFile(), metadata);
            }
        } catch (Exception e) {
            log.error("[SubAgent] 更新 metadata errorLog 失败: appId={}", appId, e);
        }
    }

    // ── SSE 事件构建辅助方法 ──

    private ServerSentEvent<String> phaseEvent(String phase, int attempt) {
        Map<String, Object> data = new HashMap<>();
        data.put("phase", phase);
        data.put("attempt", attempt);
        return ServerSentEvent.<String>builder()
                .event("phase")
                .data(JSONUtil.toJsonStr(data))
                .build();
    }

    private ServerSentEvent<String> dataEvent(String chunk) {
        Map<String, Object> data = new HashMap<>();
        data.put("d", chunk);
        return ServerSentEvent.<String>builder()
                .data(JSONUtil.toJsonStr(data))
                .build();
    }

    private ServerSentEvent<String> buildResultEvent(boolean success, int attempt, String log) {
        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("attempt", attempt);
        data.put("log", log != null ? log : "");
        return ServerSentEvent.<String>builder()
                .event("build-result")
                .data(JSONUtil.toJsonStr(data))
                .build();
    }

    private ServerSentEvent<String> doneEvent(boolean success, int totalAttempts, String summary, String aiContent) {
        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("totalAttempts", totalAttempts);
        if (summary != null) data.put("summary", summary);
        if (aiContent != null) data.put("aiContent", aiContent);
        return ServerSentEvent.<String>builder()
                .event("done")
                .data(JSONUtil.toJsonStr(data))
                .build();
    }

    private ServerSentEvent<String> errorEvent(String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("error", message);
        return ServerSentEvent.<String>builder()
                .event("error")
                .data(JSONUtil.toJsonStr(data))
                .build();
    }
}
