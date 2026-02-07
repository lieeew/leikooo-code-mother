package com.leikooo.codemother.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.leikooo.codemother.model.dto.request.user.*;
import com.leikooo.codemother.model.entity.User;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.model.vo.VerifyCodeVO;

import java.util.List;

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
     * @return 当前登录的信息
     */
    UserVO getUserLogin();

    /**
     * 用户取消登录
     * @return 是否注销成功
     */
    Boolean userLogout();

    /**
     * 判断是否是管理员
     * @return 返回是否是管理员 true-管理员 false-非管理员
     */
    boolean isAdmin();

    /**
     * todo
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * todo
     * @param records
     * @return
     */
    List<UserVO> getUserVOList(List<User> records);

    /**
     * 获取 User 信息
     * @param userId userId
     * @return User
     */
    User getUserByUerId(String userId);

    /**
     * 删除用户
     * @param userId userId
     * @return 是否成功
     */
    Boolean removeByUserId(String userId);

    /**
     * todo
     * @param userUpdateRequest
     * @return
     */
    Boolean updateUserById(UserUpdateRequest userUpdateRequest);
}
