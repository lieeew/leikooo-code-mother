package com.leikooo.codemother.model.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class AppStatisticsVO implements Serializable {
    private Long totalTokens;
    private Long totalChats;
    private Long totalToolCalls;
    private Long avgDurationMs;
}
