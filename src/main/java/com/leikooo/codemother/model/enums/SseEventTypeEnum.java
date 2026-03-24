package com.leikooo.codemother.model.enums;

import lombok.Getter;

/**
 * @author leikooo
 * SSE 推送事件类型
 */
@Getter
public enum SseEventTypeEnum {

    PHASE("phase"),
    BUILD_RESULT("build-result"),
    DONE("done"),
    ERROR("error");

    private final String value;

    SseEventTypeEnum(String value) {
        this.value = value;
    }
}
