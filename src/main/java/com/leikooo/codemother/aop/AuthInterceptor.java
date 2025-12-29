package com.leikooo.codemother.aop;

import com.leikooo.codemother.annotation.AuthCheck;
import com.leikooo.codemother.exception.BusinessException;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.model.enums.UserRoleEnum;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/29
 * @description
 */
@Aspect
@Component
public class AuthInterceptor {
    private final UserService userService;

    public AuthInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        // 没有权限要求
        if (mustRole == null) {
            return joinPoint.proceed();
        }
        UserRoleEnum requireUserRole = UserRoleEnum.getEnumByValue(mustRole);
        UserVO userLogin = userService.getUserLogin();
        UserRoleEnum currentUserEnum = UserRoleEnum.getEnumByValue(userLogin.getUserRole());
        if (requireUserRole.equals(UserRoleEnum.ADMIN) && !currentUserEnum.equals(UserRoleEnum.ADMIN)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限");
        }
        return joinPoint.proceed();
    }
}
