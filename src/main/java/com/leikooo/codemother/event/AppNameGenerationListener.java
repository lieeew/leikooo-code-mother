package com.leikooo.codemother.event;

import com.leikooo.codemother.ai.AiChatClient;
import com.leikooo.codemother.event.AppCreatedEvent;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.service.AppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * @author leikooo
 */
@Slf4j
@Component
public class AppNameGenerationListener {

    private final AiChatClient aiChatClient;
    private final AppService appService;

    public AppNameGenerationListener(AiChatClient aiChatClient, @Lazy AppService appService) {
        this.aiChatClient = aiChatClient;
        this.appService = appService;
    }

    @Async("appNameExecutor")
    @EventListener
    public void onAppCreated(AppCreatedEvent event) {
        Long appId = event.getAppId();
        String initPrompt = event.getInitPrompt();
        try {
            String appName = aiChatClient.generateAppName(initPrompt);
            App app = new App();
            app.setId(appId);
            app.setAppName(appName);
            appService.updateById(app);
            log.info("App name generated async: appId={}, appName={}", appId, appName);
        } catch (Exception e) {
            log.error("Failed to generate app name for appId={}, using default", appId, e);
            App app = new App();
            app.setId(appId);
            app.setAppName("App-" + appId);
            appService.updateById(app);
        }
    }
}
