package com.leikooo.codemother.utils;

import org.springframework.ai.chat.model.ToolContext;

import javax.validation.constraints.NotNull;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * @author leikooo
 */
public class ConversationIdUtils {

    /**
     * 获取 CONVERSATION_ID
     * @param toolContext toolContext
     * @return CONVERSATION_ID
     */
    public static String getConversationId(@NotNull ToolContext toolContext) {
        return toolContext.getContext().get(CONVERSATION_ID).toString();
    }
}
