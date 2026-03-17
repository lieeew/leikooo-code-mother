package com.leikooo.codemother.event;

import cn.hutool.core.util.StrUtil;
import com.leikooo.codemother.config.CosClientConfig;
import com.leikooo.codemother.manager.CosManager;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.entity.AppVersion;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.model.enums.VersionStatusEnum;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.AppVersionService;
import com.leikooo.codemother.utils.ProjectPathUtils;
import com.leikooo.codemother.utils.ScreenshotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 代码生成完成后异步执行截图并上传 COS，更新 App.cover。
 *
 * @author leikooo
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppScreenshotListener {

    private final AppService appService;
    private final AppVersionService appVersionService;
    private final CosManager cosManager;
    private final CosClientConfig cosClientConfig;

    @Async("deployExecutor")
    @EventListener
    public void onAppScreenshot(AppScreenshotEvent event) {
        Long appId = event.getAppId();
        try {
            App app = appService.getById(appId);
            if (app == null) {
                log.warn("[Screenshot] App 不存在: appId={}", appId);
                return;
            }

            boolean isVue = CodeGenTypeEnum.VUE_PROJECT.getValue().equals(app.getCodeGenType());
            if (isVue) {
                Integer currentVersionNum = app.getCurrentVersionNum();
                if (currentVersionNum == null || currentVersionNum <= 0) {
                    log.info("[Screenshot] Vue 项目无有效版本，跳过: appId={}", appId);
                    return;
                }
                AppVersion version = appVersionService.getByVersionNum(appId, currentVersionNum);
                if (version == null || !VersionStatusEnum.SUCCESS.name().equals(version.getStatus())) {
                    log.info("[Screenshot] Vue 项目当前版本未构建成功，跳过: appId={}, status={}",
                            appId, version != null ? version.getStatus() : "null");
                    return;
                }
            }

            String projectPath = ProjectPathUtils.getProjectPath(appId.toString());
            File screenshotFile = ScreenshotUtils.takeScreenshot(projectPath, isVue, appId.toString());
            if (screenshotFile == null || !screenshotFile.exists()) {
                log.warn("[Screenshot] 截图生成失败: appId={}", appId);
                return;
            }

            String basePath = StrUtil.nullToEmpty(cosClientConfig.getBasePath());
            if (StrUtil.isNotBlank(basePath) && !basePath.endsWith("/")) {
                basePath = basePath + "/";
            }
            String cosKey = basePath + appId + "/cover.png";
            cosManager.putObject(cosKey, screenshotFile);

            String host = StrUtil.nullToEmpty(cosClientConfig.getHost()).replaceAll("/+$", "");
            String coverUrl = StrUtil.isNotBlank(host) ? host + "/" + cosKey : cosKey;

            appService.lambdaUpdate()
                    .eq(App::getId, appId)
                    .set(App::getCover, coverUrl)
                    .update();
            log.info("[Screenshot] 封面已更新: appId={}, cover={}", appId, coverUrl);
        } catch (Exception e) {
            log.error("[Screenshot] 截图或上传失败: appId={}", appId, e);
        }
    }
}
