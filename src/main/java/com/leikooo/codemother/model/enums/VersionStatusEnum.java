package com.leikooo.codemother.model.enums;

/**
 * 版本状态枚举
 */
public enum VersionStatusEnum {
    /**
     * 源代码生成中
     */
    SOURCE_BUILDING("源代码生成中"),
    /**
     * 构建中
     */
    BUILDING("构建中"),
    /**
     * 构建成功
     */
    SUCCESS("构建成功"),
    /**
     * 需要修复
     */
    NEED_FIX("需要修复");

    private final String desc;

    VersionStatusEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
