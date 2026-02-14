package com.leikooo.codemother.event;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.leikooo.codemother.constant.ResourcePathConstant;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.service.AppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * @author leikooo
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppCodeRegeneratedListener {

    private final AppService appService;

    @Async("deployExecutor")
    @EventListener
    public void onAppCodeRegenerated(AppCodeRegeneratedEvent event) {
        Long appId = event.getAppId();
        App app = appService.getById(appId);
        if (app == null || StrUtil.isBlank(app.getDeployKey())) {
            return;
        }
        String deployPath = ResourcePathConstant.DEPLOY_DIR + File.separator + app.getDeployKey();
        try {
            FileUtil.del(deployPath);
            log.info("[Deploy] 删除旧部署目录: appId={}, path={}", appId, deployPath);
        } catch (Exception e) {
            log.error("[Deploy] 删除旧部署目录失败: appId={}", appId, e);
        }
        appService.lambdaUpdate()
                .eq(App::getId, appId)
                .set(App::getDeployKey, null)
                .update();
    }
}
