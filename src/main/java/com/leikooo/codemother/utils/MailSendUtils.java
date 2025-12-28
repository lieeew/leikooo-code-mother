package com.leikooo.codemother.utils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static com.leikooo.codemother.constant.EmailConstant.EMAIL_FORM;
import static com.leikooo.codemother.constant.EmailConstant.EMAIL_SUBJECT;

/**
 * @author leikooo
 */
@Slf4j
public class MailSendUtils {

    private static final String VERIFY_CODE_TEMPLATE = "templates/verify-code.html";

    public static void sendVerifyCode(JavaMailSender mailSender, String to, String code) {
        String content = readTemplate(code);
        sendHtml(mailSender, to, content);
    }

    private static String readTemplate(String code) {
        try {
            ClassPathResource resource = new ClassPathResource(VERIFY_CODE_TEMPLATE);
            String template = Files.readString(resource.getFile().toPath(), StandardCharsets.UTF_8);
            return template.replace("${code}", code);
        } catch (IOException e) {
            log.error("read template failure cause by: {}", e);
            throw new RuntimeException(e);
        }
    }

    public static void sendHtml(JavaMailSender mailSender, String to, String content) {
        sendHtml(mailSender, EMAIL_FORM, to, EMAIL_SUBJECT, content);
    }

    public static void sendHtml(JavaMailSender mailSender, String form, String to, String subject, String content) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.displayName());
            helper.setFrom(form);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("send email failure cause by: {}", e);
            throw new RuntimeException(e);
        }
    }

    public static void sendText(JavaMailSender mailSender, String form, String to, String subject, String content) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.displayName());
        helper.setFrom(form);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, false);
        mailSender.send(message);
    }
}
