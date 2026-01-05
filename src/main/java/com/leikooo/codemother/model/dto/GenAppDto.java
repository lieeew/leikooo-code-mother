package com.leikooo.codemother.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/1/2
 * @description
 */
@AllArgsConstructor
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
}
