package com.leikooo.codemother.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.service.AppService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 版本号缓存
 * @author leikooo
 */
@Component
public class VersionCache {

    private final Cache<String, Integer> cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    private final AppService appService;

    public VersionCache(@Lazy AppService appService) {
        this.appService = appService;
    }

    /**
     * 获取当前版本号
     */
    public Integer get(String appId) {
        return cache.get(appId, key -> {
            App app = appService.getById(appId);
            return app.getCurrentVersionNum();
        });
    }

    /**
     * 设置当前版本号
     */
    public void set(String appId, Integer versionNum) {
        cache.put(appId, versionNum);
    }

    /**
     * 清除缓存
     */
    public void invalidate(String appId) {
        cache.invalidate(appId);
    }
}
