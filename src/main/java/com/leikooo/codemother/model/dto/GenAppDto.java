package com.leikooo.codemother.model.dto;

import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
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
