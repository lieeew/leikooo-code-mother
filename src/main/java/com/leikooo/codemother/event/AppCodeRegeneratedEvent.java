package com.leikooo.codemother.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AppCodeRegeneratedEvent extends ApplicationEvent {
    private final Long appId;

    public AppCodeRegeneratedEvent(Object source, Long appId) {
        super(source);
        this.appId = appId;
    }
}
