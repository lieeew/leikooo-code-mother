package com.leikooo.codemother.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * @author leikooo
 * @description 应用删除事件，用于触发相关缓存失效
 */
@Getter
public class AppDeletedEvent extends ApplicationEvent {
    private final Long appId;
    private final Integer deletedAppPriority;

    public AppDeletedEvent(Object source, Long appId, Integer deletedAppPriority) {
        super(source);
        this.appId = appId;
        this.deletedAppPriority = deletedAppPriority;
    }
}
