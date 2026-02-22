
package com.leikooo.codemother.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leikooo.codemother.ai.AiChatClient;
import com.leikooo.codemother.ai.GenerationManager;
import com.leikooo.codemother.constant.ResourcePathConstant;
import com.leikooo.codemother.constant.UserConstant;
import com.leikooo.codemother.event.AppCreatedEvent;
import com.leikooo.codemother.exception.BusinessException;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.mapper.AppMapper;
import com.leikooo.codemother.model.dto.AppQueryDto;
import com.leikooo.codemother.model.dto.CreatAppDto;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.entity.AppVersion;
import com.leikooo.codemother.model.entity.User;
import com.leikooo.codemother.model.enums.ChatHistoryMessageTypeEnum;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.model.vo.AppStatisticsVO;
import com.leikooo.codemother.model.vo.AppVO;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.*;
import com.leikooo.codemother.utils.UuidV7Generator;
import com.leikooo.codemother.utils.VueBuildUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author leikooo
 * @description 针对表【app(App)】的数据库操作Service实现
 * @createDate 2026-01-02 12:41:31
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>
        implements AppService {
    private static final Set<String> APP_SORT_FIELDS = Set.of("createTime", "updateTime", "priority", "appName", "id");

    private final AiChatClient aiChatClient;
    private final UserService userService;
    private final GenerationManager generationManager;
    private final ObservableRecordService observableRecordService;
    private final AppVersionService appVersionService;
    private final ChatHistoryService chatHistoryService;
    private final ApplicationEventPublisher eventPublisher;

    public AppServiceImpl(AiChatClient aiChatClient, UserService userService,
                          GenerationManager generationManager, ObservableRecordService observableRecordService,
                          @Lazy AppVersionService appVersionService, ChatHistoryService chatHistoryService,
                          ApplicationEventPublisher eventPublisher) {
        this.aiChatClient = aiChatClient;
        this.userService = userService;
        this.generationManager = generationManager;
        this.observableRecordService = observableRecordService;
        this.appVersionService = appVersionService;
        this.chatHistoryService = chatHistoryService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Long createApp(CreatAppDto creatAppDto) {
        String initPrompt = creatAppDto.getCreatAppRequest().getInitPrompt();
        UserVO loginUser = creatAppDto.getLoginUser();
        synchronized (loginUser.getUserAccount().intern()) {
            App app = new App();
            app.setInitPrompt(initPrompt);
            app.setUserId(UuidV7Generator.stringToBytes(loginUser.getId()));
            app.setCurrentVersionNum(0);
            ThrowUtils.throwIf(!this.save(app), ErrorCode.SYSTEM_ERROR);
            Long id = app.getId();
            CodeGenTypeEnum codeGenTypeEnum = aiChatClient.selectGenTypeEnum(initPrompt, app.getId(), loginUser.getId());
            app.setCodeGenType(codeGenTypeEnum.getValue());
            ThrowUtils.throwIf(!this.updateById(app), ErrorCode.SYSTEM_ERROR);
            eventPublisher.publishEvent(new AppCreatedEvent(this, id, initPrompt));
            return id;
        }
    }

    @Override
    public Flux<String> genAppCode(GenAppDto genAppDto) {
        boolean chatHistory = chatHistoryService.addChatMessage(genAppDto.getAppId(), genAppDto.getMessage(),
                ChatHistoryMessageTypeEnum.USER.getValue(), genAppDto.getUserLogin().getId());
        ThrowUtils.throwIf(!chatHistory, ErrorCode.SYSTEM_ERROR, "保存消息失败");
        GenAppDto updateGenApp = getAppCodeGenEnum(genAppDto);
        String appId = updateGenApp.getAppId();
        return aiChatClient.generateCode(updateGenApp)
                .doOnSubscribe(subscription -> generationManager.register(appId, subscription::cancel))
                .doFinally(signalType -> generationManager.cancel(appId));
    }

    private GenAppDto getAppCodeGenEnum(GenAppDto genAppDto) {
        String message = genAppDto.getMessage();
        String appId = genAppDto.getAppId();
        try {
            Long appIdLong = Long.parseLong(appId);
            App app = this.lambdaQuery().eq(App::getId, appIdLong).one();
            ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
            String codeGenType = app.getCodeGenType();
            ThrowUtils.throwIf(StringUtils.isEmpty(codeGenType), ErrorCode.SYSTEM_ERROR);
            CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
            return new GenAppDto(message, appId, codeGenTypeEnum, genAppDto.getUserLogin());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 非法");
        }
    }


    @Override
    public AppVO getAppVO(Long id) {
        ThrowUtils.throwIf(Objects.isNull(id), ErrorCode.PARAMS_ERROR);
        UserVO userLogin = userService.getUserLogin();
        App app = this.getById(id);
        ThrowUtils.throwIf(Objects.isNull(app), ErrorCode.PARAMS_ERROR);
        AppVO appVO = AppVO.toVO(app, userLogin);
        getAppStatistics(appVO);
        return appVO;
    }

    @Override
    public QueryWrapper<App> getQueryWrapper(AppQueryDto appQueryDto) {
        QueryWrapper<App> queryWrapper = new QueryWrapper<>();
        if (appQueryDto == null) {
            return queryWrapper;
        }
        Long id = appQueryDto.getId();
        String appName = appQueryDto.getAppName();
        String initPrompt = appQueryDto.getInitPrompt();
        String codeGenType = appQueryDto.getCodeGenType();
        String deployKey = appQueryDto.getDeployKey();
        String userId = appQueryDto.getUserId();
        Integer priority = appQueryDto.getPriority();
        String sortField = appQueryDto.getSortField();
        String sortOrder = appQueryDto.getSortOrder();
        queryWrapper.eq(Objects.nonNull(id), "id", id)
                .like(StringUtils.isNotBlank(appName), "appName", appName)
                .like(StringUtils.isNotBlank(initPrompt), "initPrompt", initPrompt)
                .eq(StringUtils.isNotBlank(codeGenType), "codeGenType", codeGenType)
                .eq(StringUtils.isNotBlank(deployKey), "deployKey", deployKey)
                .eq(Objects.nonNull(priority), "priority", priority);
        if (StringUtils.isNotBlank(userId)) {
            queryWrapper.eq("userId", UuidV7Generator.stringToBytes(userId));
        }
        if (StrUtil.isNotBlank(sortField) && APP_SORT_FIELDS.contains(sortField)) {
            queryWrapper.orderBy(true, "ascend".equals(sortOrder), sortField);
        } else {
            queryWrapper.orderBy(true, false, "createTime");
        }
        return queryWrapper;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appPage) {
        if (CollUtil.isEmpty(appPage)) {
            return List.of();
        }
        return appPage.stream()
                .map(app -> {
                    byte[] userId = app.getUserId();
                    User user = userService.getById(userId);
                    return AppVO.toVO(app, UserVO.toVO(user));
                })
                .peek(this::getAppStatistics)
                .toList();
    }

    private void getAppStatistics(AppVO appVO) {
        if (appVO.getId() == null) {
            return;
        }
        AppStatisticsVO stats = observableRecordService.getAppStatistics(Long.parseLong(appVO.getId()));
        if (Objects.nonNull(stats)) {
            appVO.setTotalInputTokens(stats.getTotalTokens());
            appVO.setTotalOutputTokens(stats.getTotalTokens());
            appVO.setTotalConsumeTime(stats.getAvgDurationMs());
        }
    }

    @Override
    public String deployApp(Long appId) {
        ThrowUtils.throwIf(Objects.isNull(appId), ErrorCode.PARAMS_ERROR);
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        UserVO userLogin = userService.getUserLogin();
        ThrowUtils.throwIf(!Objects.equals(app.getUserId(), UuidV7Generator.stringToBytes(userLogin.getId()))
                        && !UserConstant.ADMIN_ROLE.equals(userLogin.getUserRole()),
                ErrorCode.NO_AUTH_ERROR,
                "无权限部署该应用");

        if (StrUtil.isNotBlank(app.getDeployKey())) {
            log.info("[Deploy] 应用已部署，直接返回 deployKey: appId={}, deployKey={}", appId, app.getDeployKey());
            return app.getDeployKey();
        }

        Integer currentVersionNum = app.getCurrentVersionNum();
        ThrowUtils.throwIf(currentVersionNum == null || currentVersionNum <= 0,
                ErrorCode.OPERATION_ERROR, "当前版本不存在或未生成代码");
        AppVersion version = appVersionService.getByVersionNum(appId, currentVersionNum);
        ThrowUtils.throwIf(version == null, ErrorCode.NOT_FOUND_ERROR, "版本不存在");

        String deployKey = RandomUtil.randomString(6).toUpperCase();
        String currentPath = ResourcePathConstant.GENERATED_APPS_DIR + "/" + appId + "/current";
        String deployPath = ResourcePathConstant.DEPLOY_DIR + "/" + deployKey;

        boolean isVue = CodeGenTypeEnum.VUE_PROJECT.getValue().equals(app.getCodeGenType());
        try {
            if (isVue) {
                VueBuildUtils.BuildResult buildResult = VueBuildUtils.buildVueProject(currentPath);
                ThrowUtils.throwIf(!buildResult.success(), ErrorCode.OPERATION_ERROR, "Vue 项目构建失败");
                String distPath = currentPath + "/dist";
                copyDirectory(distPath, deployPath);
            } else {
                copyDirectory(currentPath, deployPath);
            }
        } catch (IOException e) {
            log.error("[Deploy] 部署失败: appId={}", appId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败: " + e.getMessage());
        }

        app.setDeployKey(deployKey);
        boolean updated = this.lambdaUpdate()
                .eq(App::getId, appId)
                .set(App::getDeployKey, deployKey)
                .update();
        ThrowUtils.throwIf(!updated, ErrorCode.SYSTEM_ERROR, "保存部署密钥失败");

        log.info("[Deploy] 部署成功: appId={}, deployKey={}", appId, deployKey);
        return deployKey;
    }

    private void copyDirectory(String source, String target) throws IOException {
        Path sourcePath = Paths.get(source);
        Path targetPath = Paths.get(target);
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = targetPath.resolve(sourcePath.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = targetPath.resolve(sourcePath.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

}
