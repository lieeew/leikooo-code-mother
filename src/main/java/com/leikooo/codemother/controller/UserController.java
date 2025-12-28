package com.leikooo.codemother.controller;

import com.leikooo.codemother.commen.BaseResponse;
import com.leikooo.codemother.commen.ResultUtils;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.model.dto.request.SendCodeRequest;
import com.leikooo.codemother.model.dto.request.UserLoginRequest;
import com.leikooo.codemother.model.dto.request.UserRegisterRequest;
import com.leikooo.codemother.model.dto.request.VerifyCodeRequest;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.model.vo.VerifyCodeVO;
import com.leikooo.codemother.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * @author leikooo
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/send-code")
    public BaseResponse<Boolean> sendCode(@RequestBody @Valid SendCodeRequest request) {
        ThrowUtils.throwIf(Objects.isNull(request), ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        userService.sendCode(request);
        return ResultUtils.success(true);
    }

    @PostMapping("/verify-code")
    public BaseResponse<VerifyCodeVO> verifyCode(@RequestBody @Valid VerifyCodeRequest request) {
        ThrowUtils.throwIf(Objects.isNull(request), ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        return ResultUtils.success(userService.verifyCode(request));
    }

    @PostMapping("/register")
    public BaseResponse<UserVO> register(@RequestBody @Valid UserRegisterRequest request
            , HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(Objects.isNull(request), ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        String token = httpRequest.getHeader("Token");
        return ResultUtils.success(userService.register(request, token));
    }

    @PostMapping("/login")
    public BaseResponse<UserVO> userLogin(
            @RequestBody @Valid UserLoginRequest userLoginRequest
            , HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(Objects.isNull(userLoginRequest), ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        return ResultUtils.success(userService.userLogin(userLoginRequest));
    }

    /**
     * 获取登录用户
     * @param httpServletRequest httpRequest
     * @return userVO
     */
    @PostMapping("/get")
    public BaseResponse<UserVO> getUser(
          HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(Objects.isNull(httpServletRequest), ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        return ResultUtils.success(userService.getUserLogin(httpServletRequest));
    }
}
