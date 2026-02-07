package com.leikooo.codemother.ai.advisor;

import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.AppVersionService;
import com.leikooo.codemother.utils.ConversationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

/**
 * 版本保存 Advisor
 * 作用：在 AI 流完成后保存版本快照
 * 顺序：order=MAX-300（在 BuildAdvisor 之前执行）
 */
@Slf4j
@Component
public class VersionAdvisor implements StreamAdvisor {

    private final AppService appService;
    private final AppVersionService appVersionService;

    public VersionAdvisor(@Lazy AppService appService,
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
                .doFinally(signal -> {
                    if (SignalType.ON_COMPLETE.equals(signal)) {
                        try {
                            appVersionService.saveVersion(appId);
                            log.info("[VersionAdvisor] 保存版本完成: appId={}", appId);
                        } catch (Exception e) {
                            log.error("[VersionAdvisor] 保存版本失败: appId={}", appId, e);
                        }
                    }
                });
    }

    private boolean isVueProject(App app) {
        return CodeGenTypeEnum.VUE_PROJECT == CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
    }

    @Override
    public String getName() {
        return "VersionAdvisor";
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 300;
    }
}
