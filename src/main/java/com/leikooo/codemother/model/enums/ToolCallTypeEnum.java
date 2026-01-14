package com.leikooo.codemother.model.enums;

import lombok.Getter;

/**
 * @author leikooo
 */
@Getter
public enum ToolCallTypeEnum {

    CALL("call", "工具调用"),
    RESULT("result", "工具调用结果");

    private final String value;
    private final String desc;

    ToolCallTypeEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static ToolCallTypeEnum getEnumByValue(String value) {
        for (ToolCallTypeEnum anEnum : values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
