package com.leikooo.codemother.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * @author leikooo
 * @description 应用更新事件，用于触发相关缓存失效
 */
@Getter
public class AppUpdatedEvent extends ApplicationEvent {
    private final Long appId;
    private final Integer oldPriority;
    private final Integer newPriority;

    public AppUpdatedEvent(Object source, Long appId, Integer oldPriority, Integer newPriority) {
        super(source);
        this.appId = appId;
        this.oldPriority = oldPriority;
        this.newPriority = newPriority;
    }
}
