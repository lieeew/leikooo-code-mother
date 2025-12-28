package com.leikooo.codemother.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.leikooo.codemother.commen.BaseResponse;
import com.leikooo.codemother.commen.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author leikooo
 */
@RestControllerAdvice
@Slf4j
@Hidden
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public BaseResponse<?> notLoginException(NotLoginException e) {
        log.error("NotLoginException", e);
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, e.getMessage());
    }

    @ExceptionHandler(NotPermissionException.class)
    public BaseResponse<?> notPermissionExceptionHandler(NotPermissionException e) {
        log.error("NotPermissionException", e);
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NullPointerException.class)
    public BaseResponse<?> nullPointerExceptionHandler(NullPointerException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR);
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "运行时异常");
    }

    @ExceptionHandler(Exception.class)
    public BaseResponse<?> exceptionHandler(Exception e) {
        log.error("Exception", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    /**
     * 处理参数校验异常（MethodArgumentNotValidException）。
     * <p>
     * 当使用 @Valid 或 @Validated 校验请求参数失败时，会抛出该异常。
     * 本方法捕获该异常，提取所有校验错误信息，并返回统一的错误响应。
     *
     * @param e MethodArgumentNotValidException 异常对象，包含所有校验错误信息
     * @return BaseResponse 包含错误码与错误提示信息的统一响应体
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> handleException(MethodArgumentNotValidException e) {
        log.error("MethodArgumentNotValidException", e);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, e.getBindingResult().getAllErrors().get(0).getDefaultMessage());
    }
}

