package com.leikooo.codemother.event;

import com.leikooo.codemother.constant.AppConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * @author leikooo
 * @description 精选应用列表缓存失效监听器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodAppCacheEvictListener {

    private final CacheManager cacheManager;

    private void evictCache() {
        Cache cache = cacheManager.getCache("good_app_page");
        if (cache != null) {
            cache.clear();
            log.info("[CacheEvict] good_app_page 缓存已清除");
        }
    }

    private boolean isGoodApp(Integer priority) {
        return priority != null && priority.equals(AppConstant.GOOD_APP_PRIORITY);
    }

    @Async("cacheEvictExecutor")
    @EventListener
    public void onAppUpdated(AppUpdatedEvent event) {
        Integer oldPriority = event.getOldPriority();
        Integer newPriority = event.getNewPriority();
        if (isGoodApp(oldPriority) || isGoodApp(newPriority)) {
            log.info("[CacheEvict] 应用更新触发缓存失效: appId={}, oldPriority={}, newPriority={}",
                    event.getAppId(), oldPriority, newPriority);
            evictCache();
        }
    }

    @Async("cacheEvictExecutor")
    @EventListener
    public void onAppDeleted(AppDeletedEvent event) {
        if (isGoodApp(event.getDeletedAppPriority())) {
            log.info("[CacheEvict] 应用删除触发缓存失效: appId={}, deletedPriority={}",
                    event.getAppId(), event.getDeletedAppPriority());
            evictCache();
        }
    }

    @Async("cacheEvictExecutor")
    @EventListener
    public void onAppCreated(AppCreatedEvent event) {
        log.info("[CacheEvict] 应用创建触发缓存失效检查: appId={}", event.getAppId());
        // 应用创建时会自动添加到列表，需要清除缓存让新应用可见
        evictCache();
    }
}
