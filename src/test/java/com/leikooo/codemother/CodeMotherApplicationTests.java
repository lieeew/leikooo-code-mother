package com.leikooo.codemother;

import com.leikooo.codemother.model.entity.User;
import com.leikooo.codemother.service.UserService;
import com.leikooo.codemother.utils.UuidV7Generator;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.util.UUID;

@SpringBootTest
@Profile("local")
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

    @Resource
    private StreamingChatModel chatModel;

    @Test
    void contextLoads() throws InterruptedException {
//        String call = chatModel.call("你好");
//        System.out.println("call = " + call);
        Prompt prompt = new Prompt(new UserMessage("你好"));
        Flux<ChatResponse> hello = chatModel.stream(prompt);
        hello.doOnSubscribe(subscription ->  {
            System.out.println("subscription = " + subscription);
        }).doFinally(signalType -> {
            System.out.println("signalType = " + signalType);
        });
        Thread.sleep(1000000);
    }

}
