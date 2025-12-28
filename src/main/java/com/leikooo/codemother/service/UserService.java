package com.leikooo.codemother.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.leikooo.codemother.model.dto.request.SendCodeRequest;
import com.leikooo.codemother.model.dto.request.UserLoginRequest;
import com.leikooo.codemother.model.dto.request.UserRegisterRequest;
import com.leikooo.codemother.model.dto.request.VerifyCodeRequest;
import com.leikooo.codemother.model.entity.User;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.model.vo.VerifyCodeVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @author leikooo
 */
public interface UserService extends IService<User> {

    /**
     * 发送验证码
     * @param request request 请求
     */
    void sendCode(SendCodeRequest request);

    /**
     * 验证验证码
     * @param request 请求
     * @return token和用户信息
     */
    VerifyCodeVO verifyCode(VerifyCodeRequest request);

    /**
     * 注册用户
     *
     * @param request 注册用户
     * @param token
     * @return 用户脱敏信息
     */
    UserVO register(UserRegisterRequest request, String token);

    /**
     * 获取加密密码
     * @param userPassword 未加密密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);

    /**
     * 用户登录
     * @param userLoginRequest 登录请求
     * @return 用户登录
     */
    UserVO userLogin(UserLoginRequest userLoginRequest);

    /**
     * 获取登录用户的信息
     * @param httpServletRequest httpRequest
     * @return
     */
    UserVO getUserLogin(HttpServletRequest httpServletRequest);
}
