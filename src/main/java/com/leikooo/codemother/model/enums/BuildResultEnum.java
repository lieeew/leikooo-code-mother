package com.leikooo.codemother.model.enums;

/**
 * @author leikooo
 * @description VUE build 结果状态
 */
public enum BuildResultEnum {

    SUCCESS("构建成功", 0),
    ERROR("构建出错", 1),
    TIMEOUT("构建超时", 2);

    private final String desc;
    private final int value;

    BuildResultEnum(String desc, int value) {
        this.desc = desc;
        this.value = value;
    }

    public String getDesc() {
        return desc;
    }

    public int getValue() {
        return value;
    }

    public static BuildResultEnum fromValue(int value) {
        for (BuildResultEnum e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return ERROR;
    }

    public static BuildResultEnum fromDesc(String desc) {
        for (BuildResultEnum e : values()) {
            if (e.desc.equals(desc)) {
                return e;
            }
        }
        return ERROR;
    }

    public static BuildResultEnum fromExitCode(int exitCode) {
        return exitCode == 0 ? SUCCESS : ERROR;
    }
}
