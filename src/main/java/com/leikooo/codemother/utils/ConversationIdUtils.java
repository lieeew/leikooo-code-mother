package com.leikooo.codemother.utils;

import com.leikooo.codemother.model.entity.App;
import org.springframework.ai.chat.model.ToolContext;

import javax.validation.constraints.NotNull;

import java.util.Map;

import static com.leikooo.codemother.constant.AppConstant.GEN_APP_INFO;
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

    /**
     * todo 完善
     * @param context
     * @return
     */
    public static String getConversationId(@NotNull Map<String, Object> context) {
        return context.get(CONVERSATION_ID).toString();
    }

    public static App getToolContextApp(@NotNull ToolContext context) {
        return (App) context.getContext().get(GEN_APP_INFO);
    }
}
