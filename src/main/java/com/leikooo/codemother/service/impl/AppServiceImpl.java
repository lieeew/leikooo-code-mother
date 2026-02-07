
package com.leikooo.codemother.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leikooo.codemother.ai.AiChatClient;
import com.leikooo.codemother.ai.GenerationManager;
import com.leikooo.codemother.exception.BusinessException;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.mapper.AppMapper;
import com.leikooo.codemother.model.dto.AppQueryDto;
import com.leikooo.codemother.model.dto.CreatAppDto;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.entity.ObservableRecord;
import com.leikooo.codemother.model.entity.User;
import com.leikooo.codemother.model.enums.ChatHistoryMessageTypeEnum;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.model.vo.AppVO;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.*;
import com.leikooo.codemother.utils.UuidV7Generator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author leikooo
 * @description 针对表【app(App)】的数据库操作Service实现
 * @createDate 2026-01-02 12:41:31
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>
        implements AppService {

    private final AiChatClient aiChatClient;
    private final UserService userService;
    private final GenerationManager generationManager;
    private final ObservableRecordService observableRecordService;
    private final AppVersionService appVersionService;
    private final ChatHistoryService chatHistoryService;

    public AppServiceImpl(AiChatClient aiChatClient, UserService userService,
                          GenerationManager generationManager, ObservableRecordService observableRecordService,
                          @Lazy AppVersionService appVersionService, ChatHistoryService chatHistoryService) {
        this.aiChatClient = aiChatClient;
        this.userService = userService;
        this.generationManager = generationManager;
        this.observableRecordService = observableRecordService;
        this.appVersionService = appVersionService;
        this.chatHistoryService = chatHistoryService;
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
            App app = this.lambdaQuery().eq(App::getId, appId).one();
            String codeGenType = app.getCodeGenType();
            ThrowUtils.throwIf(StringUtils.isEmpty(codeGenType), ErrorCode.SYSTEM_ERROR);
            CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
            return new GenAppDto(message, appId, codeGenTypeEnum, genAppDto.getUserLogin());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
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
        if (StrUtil.isNotBlank(sortField)) {
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
        ObservableRecord appStatistics = observableRecordService.getAppStatistics(appVO.getId());
        if (Objects.nonNull(appStatistics)) {
            Long inputTokens = Optional.of(appStatistics).map(ObservableRecord::getInputTokens).orElse(0L);
            Long outputTokens = Optional.of(appStatistics).map(ObservableRecord::getOutputTokens).orElse(0L);
            Long consumeTime = Optional.of(appStatistics).map(ObservableRecord::getDurationMs).orElse(0L);

            appVO.setTotalInputTokens(inputTokens);
            appVO.setTotalOutputTokens(outputTokens);
            appVO.setTotalConsumeTime(consumeTime);
        }
    }

}
