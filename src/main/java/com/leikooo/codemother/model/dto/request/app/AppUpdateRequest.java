package com.leikooo.codemother.model.dto.request.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    private static final long serialVersionUID = 1L;
} 