package com.leikooo.codemother.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leikooo.codemother.annotation.AuthCheck;
import com.leikooo.codemother.commen.BaseResponse;
import com.leikooo.codemother.commen.ResultUtils;
import com.leikooo.codemother.constant.UserConstant;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.model.dto.request.user.*;
import com.leikooo.codemother.model.entity.User;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.model.vo.VerifyCodeVO;
import com.leikooo.codemother.service.UserService;
import com.leikooo.codemother.utils.HttpHeaderUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
        String token = HttpHeaderUtils.extractBearerToken(httpRequest.getHeader("Authorization"));
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
     * 获取当前登录用户
     * @return userVO
     */
    @PostMapping("/get")
    public BaseResponse<UserVO> getCurrentUser() {
        return ResultUtils.success(userService.getUserLogin());
    }

    @PostMapping("logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest httpServletRequest) {
        return ResultUtils.success(userService.userLogout());
    }

    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(Page.of(pageNum, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        // 数据脱敏
        Page<UserVO> userVOPage = new Page<>(pageNum, pageSize, userPage.getSize());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }
}
