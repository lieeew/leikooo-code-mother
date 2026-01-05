package com.leikooo.codemother.utils;

import com.leikooo.codemother.model.entity.User;
import com.leikooo.codemother.service.UserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/23
 * @description B+Tree 插入顺序是友好的
 */
@SpringBootTest
class UuidV7GeneratorTest {

    @Resource
    private UserService userService;

    @Test
    void generate() {

    }

    @Test
    void bytesToUuid() {
        User user = userService.lambdaQuery().eq(User::getUserAccount, "leikooo").one();
        byte[] ids = user.getId();
        String uuid = UuidV7Generator.bytesToUuid(ids);
        System.out.println("uuid = " + uuid);
    }
}