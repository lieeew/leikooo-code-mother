package com.leikooo.codemother.model.enums;

import com.leikooo.codemother.exception.BusinessException;
import com.leikooo.codemother.exception.ErrorCode;

public enum RankingTypeEnum {
    TOKENS("tokens"),
    TOOL_CALLS("toolCalls"),
    DURATION("duration");

    private final String value;

    RankingTypeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static RankingTypeEnum fromValue(String value) {
        for (RankingTypeEnum e : values()) {
            if (e.value.equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR);
    }
}
