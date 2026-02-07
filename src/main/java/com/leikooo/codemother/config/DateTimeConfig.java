package com.leikooo.codemother.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Configuration
public class DateTimeConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new Converter<String, LocalDateTime>() {
            @Override
            public LocalDateTime convert(String source) {
                if (source == null || source.isEmpty()) {
                    return null;
                }
                try {
                    String normalized = source.replace(" 00:00", "+00:00");
                    return LocalDateTime.parse(normalized);
                } catch (Exception e) {
                    try {
                        String normalized = source.replace(" 00:00", "+00:00");
                        return OffsetDateTime.parse(normalized).toLocalDateTime();
                    } catch (Exception e2) {
                        throw new IllegalArgumentException("无法解析日期: " + source);
                    }
                }
            }
        });
    }
}
