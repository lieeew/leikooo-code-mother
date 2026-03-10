package com.leikooo.codemother.ai.advisor;

import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.AppVersionService;
import com.leikooo.codemother.utils.ConversationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.Executor;

/**
 * 构建 Advisor
 * 作用：在 AI 流完成后异步执行构建并更新状态
 * 顺序：order=MAX-300（outer，doOnComplete 在 VersionAdvisor 之后触发）
 */
@Slf4j
@Component
public class BuildAdvisor implements CallAdvisor, StreamAdvisor {

    private final AppService appService;
    private final AppVersionService appVersionService;

    public BuildAdvisor(@Lazy AppService appService,
                        @Lazy AppVersionService appVersionService) {
        this.appService = appService;
        this.appVersionService = appVersionService;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String appId = ConversationUtils.getChatContext(request.context()).appId();
        App app = appService.getById(appId);

        if (!isVueProject(app)) {
            return chain.nextStream(request);
        }

        return chain.nextStream(request)
                .doOnComplete(() -> {
                    try {
                        appVersionService.updateBuildStatus(app.getId().toString());
                    } catch (Exception e) {
                        log.error("[BuildAdvisor] 更新构建状态失败: appId={}", app.getId(), e);
                    }
                });
    }

    private boolean isVueProject(App app) {
        return CodeGenTypeEnum.VUE_PROJECT == CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
    }

    @Override
    public String getName() {
        return "BuildAdvisor";
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 300;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(request);
    }
}
