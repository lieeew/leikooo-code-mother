package com.leikooo.codemother.model.dto.request.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppDeployRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    private static final long serialVersionUID = 1L;
}