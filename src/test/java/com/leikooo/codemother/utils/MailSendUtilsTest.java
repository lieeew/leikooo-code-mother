package com.leikooo.codemother.utils;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/25
 * @description
 */
@SpringBootTest
@Profile("local")
class MailSendUtilsTest {

    @Resource
    private JavaMailSender javaMailSender;

    @Test
    void sendHtml() throws Exception {
        MailSendUtils.sendVerifyCode(javaMailSender, "liangzilixue123123@outlook.com", "123456");
        System.out.println("success !！");
    }
}