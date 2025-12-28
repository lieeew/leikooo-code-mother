package com.leikooo.codemother.utils;

import org.junit.jupiter.api.Test;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/23
 * @description B+Tree 插入顺序是友好的
 */
class UuidV7GeneratorTest {

    @Test
    void generate() {
        byte[] generate = UuidV7Generator.generate();
        byte[] generate1 = UuidV7Generator.generate();
        System.out.println("generate = " + generate);
        System.out.println("generate1 = " + generate1);
    }
}