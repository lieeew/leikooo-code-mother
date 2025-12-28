package com.leikooo.codemother.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.leikooo.codemother.exception.BusinessException;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.mapper.UserMapper;
import com.leikooo.codemother.model.dto.request.SendCodeRequest;
import com.leikooo.codemother.model.dto.request.UserLoginRequest;
import com.leikooo.codemother.model.dto.request.UserRegisterRequest;
import com.leikooo.codemother.model.dto.request.VerifyCodeRequest;
import com.leikooo.codemother.model.entity.User;
import com.leikooo.codemother.model.enums.UserRoleEnum;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.model.vo.VerifyCodeVO;
import com.leikooo.codemother.service.UserService;
import com.leikooo.codemother.utils.MailSendUtils;
import com.leikooo.codemother.utils.UuidV7Generator;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.leikooo.codemother.constant.RedisConstant.*;
import static com.leikooo.codemother.constant.UserConstant.LOGIN_ATTRIBUTE;
import static com.leikooo.codemother.exception.ErrorCode.PARAMS_ERROR;

/**
 * @author leikooo
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-12-23 21:03:51
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    /**
     * cache
     */
    private static final Cache<String, String> REGISTER_CACHE = Caffeine.newBuilder()
            .maximumSize(10_00)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    private final JavaMailSender javaMailSender;
    private final RedissonClient redissonClient;

    public UserServiceImpl(JavaMailSender javaMailSender, RedissonClient redissonClient) {
        this.javaMailSender = javaMailSender;
        this.redissonClient = redissonClient;
    }

    @Override
    public void sendCode(SendCodeRequest request) {
        String email = request.getEmail();
        synchronized (email.intern()) {
            RBucket<Object> sendFlag = redissonClient.getBucket(buildRedisKey(MAIL_SEND_FLAG_PREFIX, email));
            if (Objects.nonNull(sendFlag.get())) {
                throw new RuntimeException("请勿频繁发送验证码");
            }
            String code = RandomUtil.randomNumbers(6);
            // 设置 code 5 分钟过期
            redissonClient.getBucket(buildRedisKey(MAIL_PREFIX, email)).set(code, CODE_ACTIVE_TTL, TimeUnit.SECONDS);
            // 设置成功发送的标志
            sendFlag.set(code, CODE_SEND_TTL, TimeUnit.SECONDS);
            // 使用虚拟线程发送，防止阻塞
            Thread.startVirtualThread(() -> MailSendUtils.sendVerifyCode(javaMailSender, email, code));
        }
    }

    /**
     * 构建 redis key
     * @param prefix 前缀
     * @param email 邮箱
     * @return key
     */
    private String buildRedisKey(String prefix, String email) {
        return String.format("%s:%s", prefix, email);
    }

    @Override
    public VerifyCodeVO verifyCode(VerifyCodeRequest request) {
        String email = request.getEmail();
        String code = request.getCode();
        Object cacheCode = redissonClient.getBucket(buildRedisKey(MAIL_PREFIX, email)).get();
        if (Objects.isNull(cacheCode)) {
            throw new BusinessException(PARAMS_ERROR, "邮箱不存在或者过期");
        }
        if (!code.equals(cacheCode)) {
            throw new BusinessException(PARAMS_ERROR, "验证码错误");
        }
        String token = UUID.randomUUID().toString();
        redissonClient.getBucket(buildRedisKey(MAIL_TOKEN_PREFIX, email)).set(token, TOKEN_ACTIVE_TTL, TimeUnit.SECONDS);
        return VerifyCodeVO.builder().email(email).token(token).build();
    }

    @Override
    public UserVO register(UserRegisterRequest request, String token) {
        String userName = request.getUserName();
        String userAccount = request.getUserAccount();
        String userPassword = request.getUserPassword();
        String checkPassword = request.getCheckPassword();
        String userEmail = request.getUserEmail();
        Object tokenCache = redissonClient.getBucket(buildRedisKey(MAIL_TOKEN_PREFIX, userEmail)).get();
        ThrowUtils.throwIf(StringUtils.isEmpty(token), PARAMS_ERROR, "token 不存在");
        ThrowUtils.throwIf(Objects.isNull(tokenCache), PARAMS_ERROR, "token 不存在");
        ThrowUtils.throwIf(!userPassword.equals(checkPassword), PARAMS_ERROR, "密码和确认密码不一致");
        ThrowUtils.throwIf(!token.equals(tokenCache.toString()), PARAMS_ERROR, "token 不匹配");
        synchronized (userAccount.intern()) {
            Long userExistCount = this.lambdaQuery().eq(User::getUserAccount, userAccount).count();
            ThrowUtils.throwIf(userExistCount > 0, PARAMS_ERROR, "账号重复");
            Long emailExistCount = this.lambdaQuery().eq(User::getUserEmail, userEmail).count();
            ThrowUtils.throwIf(emailExistCount > 0, PARAMS_ERROR, "邮箱已被注册");
            User insertUser = User.builder()
                    .id(UuidV7Generator.generate())
                    .userName(userName)
                    .userEmail(userEmail)
                    .userAccount(userAccount)
                    .userRole(UserRoleEnum.USER.getValue())
                    .userPassword(getEncryptPassword(userPassword))
                    .build();
            this.save(insertUser);
            return UserVO.toVO(insertUser);
        }
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String salt = "leikooo";
        return DigestUtils.md5DigestAsHex((salt + userPassword).getBytes());
    }

    @Override
    public UserVO userLogin(UserLoginRequest userLoginRequest) {
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        User user = this.lambdaQuery().eq(User::getUserAccount, userAccount)
                .eq(User::getUserPassword, getEncryptPassword(userPassword)).one();
        ThrowUtils.throwIf(Objects.isNull(user), PARAMS_ERROR, "账号或者用户名错误");
        UserVO userVO = UserVO.toVO(user);
        // 使用 STP 进行登录
        StpUtil.login(userVO.getId(), SaLoginParameter.create().setExtra(LOGIN_ATTRIBUTE, userVO));
        return userVO;
    }

    @Override
    public UserVO getUserLogin(HttpServletRequest httpServletRequest) {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (Objects.isNull(loginId)) {
            return new UserVO();
        }
        return (UserVO) StpUtil.getExtra(LOGIN_ATTRIBUTE);
    }

}




