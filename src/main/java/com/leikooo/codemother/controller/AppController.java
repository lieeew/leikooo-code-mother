package com.leikooo.codemother.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leikooo.codemother.annotation.AuthCheck;
import com.leikooo.codemother.commen.BaseResponse;
import com.leikooo.codemother.commen.ResultUtils;
import com.leikooo.codemother.constant.UserConstant;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.model.dto.AppQueryDto;
import com.leikooo.codemother.model.dto.CreatAppDto;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.dto.request.app.AppQueryRequest;
import com.leikooo.codemother.model.dto.request.app.CreatAppRequest;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.vo.AppVO;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.UserService;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/1/2
 * @description
 */
@RestController
@RequestMapping("/app")
public class AppController {
    private final AppService appService;
    private final UserService userService;

    public AppController(AppService appService, UserService userService) {
        this.appService = appService;
        this.userService = userService;
    }

    @PostMapping("/add")
    public BaseResponse<Long> createApp(@Valid @RequestBody CreatAppRequest creatAppRequest) {
        ThrowUtils.throwIf(Objects.isNull(creatAppRequest), ErrorCode.PARAMS_ERROR);
        UserVO userLogin = userService.getUserLogin();
        return ResultUtils.success(appService.createApp(new CreatAppDto(creatAppRequest, userLogin)));
    }

    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> generateApp(
            @RequestParam(name = "appId") Long appId,
            @RequestParam(name = "message") String message
    ) {
        ThrowUtils.throwIf(StringUtils.isEmpty(message) || Objects.isNull(appId), ErrorCode.PARAMS_ERROR);
        GenAppDto genAppDto = new GenAppDto(message, appId.toString());
        Flux<String> stringFlux = appService.genAppCode(genAppDto);
        return stringFlux
                .map(chunk -> {
                    Map<String, String> wrapper = Map.of("d", chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(
                        // 发送结束事件
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                )).doFinally(signalType -> {
                    System.out.println("signalType = " + signalType);
                });
    }

    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVO(@RequestParam(name = "id") Long id) {
        ThrowUtils.throwIf(Objects.isNull(id), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(appService.getAppVO(id));
    }

    @AuthCheck(mustRole = UserConstant.USER_ROLE)
    @PostMapping("/my/list/page/vo")
    public BaseResponse<List<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        int pageSize = appQueryRequest.getPageSize();
        int current = appQueryRequest.getCurrent();
        ThrowUtils.throwIf(pageSize > 50, ErrorCode.PARAMS_ERROR);
        UserVO userLogin = userService.getUserLogin();
        Page<App> page = appService.page(
                Page.of(current, pageSize),
                appService.getQueryWrapper(AppQueryDto.toDto(appQueryRequest, userLogin))
        );
        return ResultUtils.success(appService.getAppVOList(page));
    }
}
