package com.leikooo.codemother.ai.advisor;

import com.leikooo.codemother.ai.AiChatClient;
import com.leikooo.codemother.constant.ResourcePathConstant;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.enums.BuildResultEnum;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.utils.ConversationIdUtils;
import com.leikooo.codemother.utils.VueBuildUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author leikooo
 * @description 校验错误 && 进行修复
 */
@Slf4j
@Component
public class BuildAdvisor implements CallAdvisor, StreamAdvisor {

    private final AppService appService;
    private final AiChatClient aiChatClient;

    public BuildAdvisor(@Lazy AppService appService, @Lazy AiChatClient aiChatClient) {
        this.appService = appService;
        this.aiChatClient = aiChatClient;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        String appId = ConversationIdUtils.getConversationId(chatClientRequest.context());
        App app = appService.getById(appId);
        if (Objects.isNull(app)) {
            log.error("app 不存在 appId {}", appId);
            return streamAdvisorChain.nextStream(chatClientRequest);
        }
        String projectPath = ResourcePathConstant.ROOT_PATH + File.separator + appId;

        return streamAdvisorChain.nextStream(chatClientRequest)
                .doFinally(signalType -> {
                    try {
                        String codeGenType = app.getCodeGenType();
                        CodeGenTypeEnum enumByValue = CodeGenTypeEnum.getEnumByValue(codeGenType);
                        if (enumByValue == CodeGenTypeEnum.VUE_PROJECT) {
                            VueBuildUtils.BuildResult buildResult = VueBuildUtils.buildVueProject(projectPath);
                            BuildResultEnum resultEnum = BuildResultEnum.fromExitCode(buildResult.exitCode());
                            log.info("[BuildAdvisor] Build result: {}, exitCode={}", resultEnum, buildResult.exitCode());
                            if (resultEnum == BuildResultEnum.ERROR) {
                                handleBuildResult(app, buildResult);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Vue build failed for path: {}", projectPath, e);
                    }
                });
    }

    private void handleBuildResult(App app, VueBuildUtils.BuildResult buildResult) {
        try {
            String analysisPrompt = buildAnalysisPrompt(buildResult);
            BuildResultEnum analysisResult = aiChatClient.checkBuildResult(analysisPrompt, app.getId());
            log.info("[BuildAdvisor] Build error analysis:\n{}", analysisResult);

            if (BuildResultEnum.SUCCESS.equals(analysisResult)) {
                log.info("[BuildAdvisor] Build fixed successfully for app: {}", app.getId());
                return;
            }

            log.info("[BuildAdvisor] Attempting to fix build error for app: {}", app.getId());
            String appId = app.getId().toString();
            String errorLog = buildResult.errorLog();
            String fixPrompt = buildFixPrompt(app, errorLog);
            Flux<String> fixFlux = appService.fixBuildError(appId, fixPrompt);

            fixFlux.doOnNext(fixedCode -> {
                log.info("[BuildAdvisor] Fix generated for app: {}, retrying build...", appId);
                VueBuildUtils.BuildResult retryResult = VueBuildUtils.buildVueProject(ResourcePathConstant.ROOT_PATH + File.separator + appId);
                BuildResultEnum retryResultEnum = BuildResultEnum.fromExitCode(retryResult.exitCode());

                if (retryResultEnum == BuildResultEnum.ERROR) {
                    handleBuildResult(app, retryResult);
                } else {
                    log.info("[BuildAdvisor] Build succeeded after fix for app: {}", appId);
                }
            }).doOnError(e -> log.error("[BuildAdvisor] Failed to fix build error for app: {}", app.getId(), e)).subscribe();

        } catch (Exception e) {
            log.error("[BuildAdvisor] Failed to analyze build error", e);
        }
    }

    private String buildFixPrompt(App app, String errorLog) {
        return String.format("""
                原始需求：%s
                
                构建失败，请根据以下错误日志修复代码：
                
                === 错误日志 ===
                %s
                
                请分析错误原因，修复所有问题，重新生成符合需求的完整代码。
                输出完整的代码内容，不要省略任何部分。
                """, app.getInitPrompt(), errorLog);
    }

    private String buildAnalysisPrompt(VueBuildUtils.BuildResult buildResult) {
        String fullLog = buildResult.fullLog();
        String errorLog = buildResult.errorLog();

        if (errorLog == null || errorLog.isEmpty()) {
            return String.format("""
                    请分析以下 Vue build 日志，判断是否有错误：
                    
                    === Build Full Log ===
                    %s
                    
                    请按照系统提示词的指示进行分析并输出结果。
                    """, fullLog);
        }

        return String.format("""
                请分析以下 Vue build 日志，判断是否有错误：
                
                === Build Full Log ===
                %s
                
                === Error Lines ===
                %s
                
                请按照系统提示词的指示进行分析并输出结果。
                """, fullLog, errorLog);
    }

    @Override
    public String getName() {
        return "BuildAdvisor";
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 200;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        return callAdvisorChain.nextCall(chatClientRequest);
    }
}
