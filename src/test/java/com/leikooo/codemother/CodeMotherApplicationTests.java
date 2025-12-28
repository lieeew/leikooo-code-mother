package com.leikooo.codemother;

import com.leikooo.codemother.model.entity.User;
import com.leikooo.codemother.service.UserService;
import com.leikooo.codemother.utils.UuidV7Generator;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.ByteBuffer;
import java.util.UUID;

@SpringBootTest
class CodeMotherApplicationTests {

    @Resource
    private UserService userService;

    private byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    @Test
    public void test() throws InterruptedException {
        byte[] generate = UuidV7Generator.generate();
        User insertUser = User.builder()
                .id(generate)
                .userAccount("user")
                .userAccount("leikooo")
                .userName("leikooo")
                .userPassword("leikooo")
                .build();
        userService.save(insertUser);
    }

    @Test
    void contextLoads() {
    }

}
