package com.leikooo.codemother.ai.tools;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.leikooo.codemother.utils.ConversationUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/31
 * @description
 */
@Component
public class TodolistTools extends BaseTools {

    /**
     * cache
     */
    private static final Cache<String, String> TODOLIST_CACHE = Caffeine.newBuilder()
            .maximumSize(10_00)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    @Tool(description = "Write or update the todo list for current task. " +
            "Use this to track progress and plan remaining work. " +
            "Each todo item should be a clear, actionable task. " +
            "Format: numbered list with status markers like [ ], [x], [>], [-]. " +
            "Status meanings: [ ] pending, [x] completed, [>] in progress, [-] blocked/cancelled."
    )
    public String todoWrite(
            @ToolParam(description = "The todo list content to save. Format as a structured list with status indicators.")
            String todoContent,
            ToolContext toolContext
    ) {
        String conversationId = ConversationUtils.getToolsContext(toolContext).appId();
        if (StringUtils.isBlank(todoContent)) {
            TODOLIST_CACHE.invalidate(conversationId);
            return "Todo list cleared.";
        }
        TODOLIST_CACHE.put(conversationId, todoContent);
        return "Todo list saved successfully.\n\nCurrent todo list:\n" + todoContent;
    }

    @Tool(description = "Read the current todo list for this conversation. " +
            "Use this to check progress and see what tasks remain. " +
            "Returns an empty message if no todo list exists yet."
    )
    public String todoRead(ToolContext toolContext) {
        String conversationId = ConversationUtils.getToolsContext(toolContext).appId();
        String todoContent = TODOLIST_CACHE.getIfPresent(conversationId);
        if (StringUtils.isBlank(todoContent)) {
            return "No todo list for this conversation.";
        }
        return "Current todo list:\n" + todoContent;
    }

    @Override
    String getToolName() {
        return "Todo List Tool";
    }

    @Override
    String getToolDes() {
        return "Read and write task todo lists to track progress";
    }
}
