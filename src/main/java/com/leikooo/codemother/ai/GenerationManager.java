package com.leikooo.codemother.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author leikooo
 */
@Component
@Slf4j
public class GenerationManager {
    private final ConcurrentHashMap<String, Runnable> activeGenerations = new ConcurrentHashMap<>();

    public void register(String conversationId, Runnable cancelHandler) {
        activeGenerations.put(conversationId, cancelHandler);
        log.info("[GenerationManager] Registered generation for conversation: {}", conversationId);
    }

    public boolean cancel(String conversationId) {
        Runnable cancelHandler = activeGenerations.remove(conversationId);
        if (cancelHandler != null) {
            cancelHandler.run();
            log.info("[GenerationManager] Cancelled generation for conversation: {}", conversationId);
            return true;
        }
        log.warn("[GenerationManager] No active generation found for conversation: {}", conversationId);
        return false;
    }

    public boolean isGenerating(String conversationId) {
        return activeGenerations.containsKey(conversationId);
    }
}
