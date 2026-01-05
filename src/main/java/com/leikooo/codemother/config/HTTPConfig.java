package com.leikooo.codemother.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * @author leikooo
 */
@Configuration
public class HTTPConfig {

    @Value("${http.client.connect-timeout:600s}")
    private Duration connectTimeout;

    @Value("${http.client.read-timeout:600s}")
    private Duration readTimeout;

    @Bean
    public ClientHttpRequestFactorySettings clientHttpRequestFactorySettings() {
        return new ClientHttpRequestFactorySettings(ClientHttpRequestFactorySettings.Redirects.FOLLOW_WHEN_POSSIBLE, connectTimeout,
                readTimeout, null);
    }
}