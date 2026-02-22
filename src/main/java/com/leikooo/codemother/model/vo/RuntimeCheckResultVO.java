package com.leikooo.codemother.model.vo;

import java.util.List;

public record RuntimeCheckResultVO(
        boolean hasErrors,
        List<String> consoleErrors,
        List<String> jsExceptions,
        boolean hasScreenshot,
        String checkTime
) {}
