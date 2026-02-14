package com.leikooo.codemother.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AppCreatedEvent extends ApplicationEvent {
    private final Long appId;
    private final String initPrompt;

    public AppCreatedEvent(Object source, Long appId, String initPrompt) {
        super(source);
        this.appId = appId;
        this.initPrompt = initPrompt;
    }
}
