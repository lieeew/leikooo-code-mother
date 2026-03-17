package com.leikooo.codemother.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 代码生成完成后触发截图并上传 COS
 *
 * @author leikooo
 */
@Getter
public class AppScreenshotEvent extends ApplicationEvent {
    private final Long appId;

    public AppScreenshotEvent(Object source, Long appId) {
        super(source);
        this.appId = appId;
    }
}
