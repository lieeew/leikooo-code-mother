package com.leikooo.codemother.model.dto;

import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.model.vo.UserVO;
import lombok.Getter;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/1/2
 * @description
 */
@Getter
public class GenAppDto {

    /**
     * message
     */
    private String message;

    /**
     * app id
     */
    private String appId;

    /**
     * 生成代码的类型
     */
    private CodeGenTypeEnum codeGenTypeEnum;

    /**
     * userLogin
     */
    private UserVO userLogin;

    public GenAppDto(String message, String appId, CodeGenTypeEnum codeGenTypeEnum, UserVO userLogin) {
        this.message = message;
        this.appId = appId;
        this.codeGenTypeEnum = codeGenTypeEnum;
        this.userLogin = userLogin;
    }

    public GenAppDto(String message, String appId, UserVO userLogin) {
        this.message = message;
        this.appId = appId;
        this.userLogin = userLogin;
    }

    public GenAppDto(String message, String appId) {
        this.message = message;
        this.appId = appId;
    }

    public GenAppDto(String message, String appId, CodeGenTypeEnum codeGenTypeEnum) {
        this.message = message;
        this.appId = appId;
        this.codeGenTypeEnum = codeGenTypeEnum;
    }
}
