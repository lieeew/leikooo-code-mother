
package com.leikooo.codemother.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leikooo.codemother.ai.AiChatClient;
import com.leikooo.codemother.ai.MessageAggregator;
import com.leikooo.codemother.ai.tools.ToolEventPublisher;
import com.leikooo.codemother.constant.ResourcePathConstant;
import com.leikooo.codemother.exception.BusinessException;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.mapper.AppMapper;
import com.leikooo.codemother.model.dto.AppQueryDto;
import com.leikooo.codemother.model.dto.CreatAppDto;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.entity.SpringAiChatMemory;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.model.vo.AppVO;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.SpringAiChatMemoryService;
import com.leikooo.codemother.service.UserService;
import com.leikooo.codemother.utils.UuidV7Generator;
import com.leikooo.codemother.utils.VueBuildUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
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
    private final ToolEventPublisher toolEventPublisher;
    private final SpringAiChatMemoryService springAiChatMemoryService;

    public AppServiceImpl(AiChatClient aiChatClient, UserService userService, ToolEventPublisher toolEventPublisher, @Lazy SpringAiChatMemoryService springAiChatMemoryService) {
        this.aiChatClient = aiChatClient;
        this.userService = userService;
        this.toolEventPublisher = toolEventPublisher;
        this.springAiChatMemoryService = springAiChatMemoryService;
    }

    @Override
    public Long createApp(CreatAppDto creatAppDto) {
        String initPrompt = creatAppDto.getCreatAppRequest().getInitPrompt();
        UserVO loginUser = creatAppDto.getLoginUser();
        synchronized (loginUser.getUserAccount().intern()) {
            App app = new App();
            app.setInitPrompt(initPrompt);
            app.setUserId(UuidV7Generator.stringToBytes(loginUser.getId()));
            CodeGenTypeEnum codeGenTypeEnum = aiChatClient.selectGenTypeEnum(initPrompt);
            app.setCodeGenType(codeGenTypeEnum.getValue());
            ThrowUtils.throwIf(!this.save(app), ErrorCode.SYSTEM_ERROR);
            return app.getId();
        }
    }

    @Override
    public Flux<String> genAppCode(GenAppDto genAppDto) {
        String appId = genAppDto.getAppId();
        GenAppDto updateGenApp = getAppCodeGenEnum(genAppDto);
        Flux<ChatClientResponse> chatClientResponseFlux = aiChatClient.generateCode(updateGenApp);
        CodeGenTypeEnum finalCodeGenType = updateGenApp.getCodeGenTypeEnum();
        String projectPath = ResourcePathConstant.ROOT_PATH + File.separator + appId;
        Flux<String> codeFlux = chatClientResponseFlux
                .map(response -> Optional.ofNullable(response.chatResponse()).map(ChatResponse::getResult).map(Generation::getOutput).map(AbstractMessage::getText).orElse(""))
                .doFinally(signalType -> {
                    toolEventPublisher.complete(appId);
                    if (finalCodeGenType.equals(CodeGenTypeEnum.VUE_PROJECT)) {
                        VueBuildUtils.buildVueProject(projectPath);
                    }
                });
        Flux<String> toolFlux = getToolEventFlux(appId);
        StringBuilder resultCollector = new StringBuilder();
        return MessageAggregator.aggregateString(Flux.merge(codeFlux, toolFlux), resultCollector)
                .doFinally(signalType -> {
                    try {
                        springAiChatMemoryService.lambdaUpdate()
                                .eq(SpringAiChatMemory::getConversationId, appId)
                                .eq(SpringAiChatMemory::getType, MessageType.ASSISTANT)
                                .orderByDesc(SpringAiChatMemory::getTimestamp)
                                .last("LIMIT 1")
                                .set(SpringAiChatMemory::getContent, resultCollector.toString())
                                .update();
                    } catch (Exception e) {
                        log.error("保存包含工具调用的完整记录报错 {}", e);
                    }
                });
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

    /**
     * 工具调用推送流
     * @param sessionId sessionId
     * @return flux
     */
    private Flux<String> getToolEventFlux(String sessionId) {
        return toolEventPublisher.events(sessionId)
                .map(event -> {
                    Object result = Optional.ofNullable(event.result()).orElse("");
                    final String methodName = event.methodName();
                    String message = switch (event.type()) {
                        case "tool_call" -> String.format("正在进行工具调用 %s: %s", methodName, result);
                        case "tool_result" -> String.format("工具调用完成 %s: %s", methodName, result);
                        default -> "";
                    };
                    return String.format("\n\n[选择工具] %s \n\n", message);
                }).doOnNext(toolInfo -> {
//                    try {
//                        SpringAiChatMemory springAiChatMemory = SpringAiChatMemory.builder()
//                                .content(toolInfo)
//                                .type(MessageType.ASSISTANT)
//                                .conversationId(sessionId)
//                                .timestamp(new Date())
//                                .build();
//                        springAiChatMemoryService.save(springAiChatMemory);
//                    } catch (Exception e) {
//                        log.error("保存 tool 调用信息失败 {}", ExceptionUtils.getRootCauseMessage(e));
//                    }
                });
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
