package com.leikooo.codemother.service.impl;

import cn.hutool.core.text.StrBuilder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leikooo.codemother.ai.AiChatClient;
import com.leikooo.codemother.ai.ToolEventPublisher;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.model.dto.CreatAppDto;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.vo.AppVO;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.mapper.AppMapper;
import com.leikooo.codemother.service.UserService;
import com.leikooo.codemother.utils.UuidV7Generator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Objects;

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
        Flux<ChatClientResponse> chatClientResponseFlux = aiChatClient.generateCode(genAppDto);
        StrBuilder sb = new StrBuilder();
        Flux<String> codeFlux = chatClientResponseFlux
                .map(response -> {
                    String text = response.chatResponse().getResult().getOutput().getText();
                    sb.append(text);
                    log.info("response = {}", text);
                    return text;
                })
                .doFinally(signalType -> {
                    log.info("signalType = {}", signalType);
                    log.error("result = {}", sb);
                });
        Flux<String> toolEventFlux = toolEventPublisher.events(sessionId)
                .map(event -> {
                    String message = switch (event.type()) {
                        case "tool_call" -> String.format("%s: %s", "正在进行工具调用", event.methodName());
                        case "tool_result" -> String.format("%s: %s", "工具调用完成", event.methodName());
                        default -> "";
                    };
                    return String.format("\n\n[选择工具] %s \n\n", message);
                });
        return Flux.merge(codeFlux, toolEventFlux);
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




