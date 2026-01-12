package com.leikooo.codemother.service.impl;

import cn.hutool.core.text.StrBuilder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leikooo.codemother.ai.AiChatClient;
import com.leikooo.codemother.ai.tools.ToolEventPublisher;
import com.leikooo.codemother.constant.ResourcePathConstant;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.model.dto.CreatAppDto;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.model.vo.AppVO;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.mapper.AppMapper;
import com.leikooo.codemother.service.UserService;
import com.leikooo.codemother.utils.UuidV7Generator;
import com.leikooo.codemother.utils.VueBuildUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
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

    public AppServiceImpl(AiChatClient aiChatClient, UserService userService, ToolEventPublisher toolEventPublisher) {
        this.aiChatClient = aiChatClient;
        this.userService = userService;
        this.toolEventPublisher = toolEventPublisher;
    }

    @Override
    public Long createApp(CreatAppDto creatAppDto) {
        String initPrompt = creatAppDto.getCreatAppRequest().getInitPrompt();
        UserVO loginUser = creatAppDto.getLoginUser();
        synchronized (loginUser.getUserAccount().intern()) {
            App app = new App();
            app.setInitPrompt(initPrompt);
            app.setUserId(UuidV7Generator.stringToBytes(loginUser.getId()));
            this.save(app);
            return app.getId();
        }
    }

    @Override
    public Flux<String> genAppCode(GenAppDto genAppDto) {
        String sessionId = genAppDto.getAppId();
        GenAppDto updateGenApp = generateGenAppType(genAppDto);
        Flux<ChatClientResponse> chatClientResponseFlux = aiChatClient.generateCode(updateGenApp);
        Flux<String> codeFlux = chatClientResponseFlux
                .map(response -> Optional.ofNullable(response.chatResponse()).map(ChatResponse::getResult).map(Generation::getOutput).map(AbstractMessage::getText).orElse(""))
                .doFinally(signalType -> {
                    CodeGenTypeEnum codeGenTypeEnum = updateGenApp.getCodeGenTypeEnum();
                    // vue 项目需要执行 npm & build
                    if (codeGenTypeEnum.equals(CodeGenTypeEnum.VUE_PROJECT)) {
                        VueBuildUtils.buildVueProject(ResourcePathConstant.ROOT_PATH + File.separator + sessionId);
                    }
                });
        Flux<String> toolEventFlux = getToolEventFlux(sessionId);
        return Flux.merge(codeFlux, toolEventFlux);
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
                    final String toolName = event.toolName();
                    String message = switch (event.type()) {
                        case "tool_call" -> String.format("正在进行工具调用 %s: %s", toolName, result);
                        case "tool_result" -> String.format("工具调用完成 %s: %s", toolName, result);
                        default -> "";
                    };
                    return String.format("\n\n[选择工具] %s \n\n", message);
                });
    }

    private GenAppDto generateGenAppType(GenAppDto genAppDto) {
        CodeGenTypeEnum codeGenTypeEnum = aiChatClient.selectGenTypeEnum(genAppDto.getMessage());
        boolean isUpdate = this.lambdaUpdate().eq(App::getId, genAppDto.getAppId())
                .set(App::getCodeGenType, codeGenTypeEnum.getValue()).update();
        ThrowUtils.throwIf(!isUpdate, ErrorCode.SYSTEM_ERROR, "更新 APP 的生成枚举类失败");
        return new GenAppDto(genAppDto.getMessage(), genAppDto.getAppId(), codeGenTypeEnum);
    }

    @Override
    public AppVO getAppVO(Long id) {
        ThrowUtils.throwIf(Objects.isNull(id), ErrorCode.PARAMS_ERROR);
        UserVO userLogin = userService.getUserLogin();
        App app = this.getById(id);
        ThrowUtils.throwIf(Objects.isNull(app), ErrorCode.PARAMS_ERROR);
        return AppVO.toVO(app, userLogin);
    }

}




