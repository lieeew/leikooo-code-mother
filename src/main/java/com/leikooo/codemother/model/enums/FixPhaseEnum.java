package com.leikooo.codemother.model.enums;

import lombok.Getter;

/**
 * @author leikooo
 * SubAgent 修复流程阶段
 */
@Getter
public enum FixPhaseEnum {

    FIXING("fixing"),
    BUILDING("building");

    private final String value;

    FixPhaseEnum(String value) {
        this.value = value;
    }
}
