package com.leikooo.codemother.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * @author leikooo
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager() {{
            setCaffeine(Caffeine.newBuilder()
                    .maximumSize(100)
                    .expireAfterWrite(1, TimeUnit.HOURS));
        }};
    }
}
