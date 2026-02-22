package com.leikooo.codemother.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/2/14
 * @description
 */
class RuntimeCheckUtilsTest {

    @Test
    void checkRuntime() {
        RuntimeCheckUtils.RuntimeCheckResult runtimeCheckResult = RuntimeCheckUtils.checkRuntime("temp/generated-apps/2022617916159266817/current", "2022617916159266817");
        System.out.println("runtimeCheckResult = " + runtimeCheckResult);
    }
}