package com.leikooo.codemother.utils;

import com.leikooo.codemother.model.dto.ChatContext;
import com.leikooo.codemother.model.dto.ToolsContext;
import org.springframework.ai.chat.model.ToolContext;

import javax.validation.constraints.NotNull;
import java.util.Map;

import static com.leikooo.codemother.constant.AppConstant.GEN_APP_INFO;

/**
 * @author leikooo
 */
public class ConversationUtils {

    /**
     * 获取 ToolsContext
     * @param toolContext toolContext
     * @return ToolsContext
     */
    public static ToolsContext getToolsContext(@NotNull ToolContext toolContext) {
        return (ToolsContext) toolContext.getContext().get(GEN_APP_INFO);
    }

    /**
     * ChatContext
     * @param context context
     * @return ChatContext
     */
    public static ChatContext getChatContext(@NotNull Map<String, Object> context) {
        return (ChatContext) context.get(GEN_APP_INFO);
    }
}
