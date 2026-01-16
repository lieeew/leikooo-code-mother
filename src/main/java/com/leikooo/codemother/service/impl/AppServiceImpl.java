
package com.leikooo.codemother.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leikooo.codemother.ai.AiChatClient;
import com.leikooo.codemother.ai.tools.ToolEventPublisher;
import com.leikooo.codemother.exception.BusinessException;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.mapper.AppMapper;
import com.leikooo.codemother.model.dto.AppQueryDto;
import com.leikooo.codemother.model.dto.CreatAppDto;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.model.vo.AppVO;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.UserService;
import com.leikooo.codemother.utils.UuidV7Generator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

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

    public AppServiceImpl(AiChatClient aiChatClient, UserService userService) {
        this.aiChatClient = aiChatClient;
        this.userService = userService;
    }

    @Override
    public Long createApp(CreatAppDto creatAppDto) {
        String initPrompt = creatAppDto.getCreatAppRequest().getInitPrompt();
        UserVO loginUser = creatAppDto.getLoginUser();
        synchronized (loginUser.getUserAccount().intern()) {
            App app = new App();
            app.setInitPrompt(initPrompt);
            app.setUserId(UuidV7Generator.stringToBytes(loginUser.getId()));
            ThrowUtils.throwIf(!this.save(app), ErrorCode.SYSTEM_ERROR);
            Long id = app.getId();
            CodeGenTypeEnum codeGenTypeEnum = aiChatClient.selectGenTypeEnum(initPrompt, app.getId());
            app.setCodeGenType(codeGenTypeEnum.getValue());
            ThrowUtils.throwIf(!this.updateById(app), ErrorCode.SYSTEM_ERROR);
            return id;
        }
    }

    @Override
    public Flux<String> genAppCode(GenAppDto genAppDto) {
        String appId = genAppDto.getAppId();
        GenAppDto updateGenApp = getAppCodeGenEnum(genAppDto);
        Flux<ChatClientResponse> chatClientResponseFlux = aiChatClient.generateCode(updateGenApp);
        return chatClientResponseFlux
                .map(response -> Optional.ofNullable(response.chatResponse()).map(ChatResponse::getResult).map(Generation::getOutput).map(AbstractMessage::getText).orElse(""));
    }

    @Override
    public Flux<String> fixBuildError(String appId, String errorAnalysis) {
        App app = this.lambdaQuery().eq(App::getId, appId).one();
        if (app == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "App not found: " + appId);
        }

        String initPrompt = app.getInitPrompt();
        String fixPrompt = String.format("""
                原始需求：%s
                
                构建失败，请根据以下错误分析修复代码：
                
                === 错误分析 ===
                %s
                
                请重新生成符合需求的代码，修复所有构建错误。
                """, initPrompt, errorAnalysis);

        GenAppDto fixDto = new GenAppDto(fixPrompt, appId, CodeGenTypeEnum.getEnumByValue(app.getCodeGenType()));
        Flux<ChatClientResponse> chatClientResponseFlux = aiChatClient.generateCode(fixDto);
        return chatClientResponseFlux
                .map(response -> Optional.ofNullable(response.chatResponse()).map(ChatResponse::getResult).map(Generation::getOutput).map(AbstractMessage::getText).orElse(""));
    }

    private GenAppDto getAppCodeGenEnum(GenAppDto genAppDto) {
        String message = genAppDto.getMessage();
        String appId = genAppDto.getAppId();
        try {
            App app = this.lambdaQuery().eq(App::getId, appId).one();
            String codeGenType = app.getCodeGenType();
            ThrowUtils.throwIf(StringUtils.isEmpty(codeGenType), ErrorCode.SYSTEM_ERROR);
            CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
            return new GenAppDto(message, appId, codeGenTypeEnum);
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
        return AppVO.toVO(app, userLogin);
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
        String sortField = appQueryDto.getSortField();
        String sortOrder = appQueryDto.getSortOrder();
        queryWrapper.eq(Objects.nonNull(id), "id", id)
                .like(StringUtils.isNotBlank(appName), "appName", appName)
                .like(StringUtils.isNotBlank(initPrompt), "initPrompt", initPrompt)
                .eq(StringUtils.isNotBlank(codeGenType), "codeGenType", codeGenType)
                .eq(StringUtils.isNotBlank(deployKey), "deployKey", deployKey);
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
    public List<AppVO> getAppVOList(Page<App> appPage) {
        UserVO userVO = userService.getUserLogin();
        return appPage.getRecords().stream()
                .map(app -> AppVO.toVO(app, userVO)).toList();
    }

}
