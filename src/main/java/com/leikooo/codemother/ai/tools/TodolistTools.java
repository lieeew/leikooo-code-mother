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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/31
 * @description
 */
@Component
public class TodolistTools extends BaseTools {

    private static final int MAX_TODOS = 20;
    private static final Set<String> VALID_STATUSES = Set.of("pending", "in_progress", "completed");
    private static final Map<String, String> STATUS_MARKERS = Map.of(
            "pending", "[ ]",
            "in_progress", "[>]",
            "completed", "[x]"
    );

    private record TodoItem(String id, String text, String status) {
    }

    private static final Cache<String, List<TodoItem>> TODOLIST_CACHE = Caffeine.newBuilder()
            .maximumSize(10_00)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    @Tool(description = "Update task list. Track progress on multi-step tasks. Pass the full list of items each time (replaces previous list). " +
            "Each item must have id, text, status. Status: pending, in_progress, completed. Only one item can be in_progress."
    )
    public String todoUpdate(
            @ToolParam(description = "Full list of todo items. Each item: id (string), text (string), status (pending|in_progress|completed).")
            List<Map<String, Object>> items,
            ToolContext toolContext
    ) {
        try {
            String conversationId = ConversationUtils.getToolsContext(toolContext).appId();
            if (items == null || items.isEmpty()) {
                TODOLIST_CACHE.invalidate(conversationId);
                return "No todos.";
            }
            if (items.size() > MAX_TODOS) {
                return "Error: Max " + MAX_TODOS + " todos allowed";
            }
            List<TodoItem> validated = validateAndConvert(items);
            TODOLIST_CACHE.put(conversationId, validated);
            return render(validated);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
    }

    private List<TodoItem> validateAndConvert(List<Map<String, Object>> items) {
        int inProgressCount = 0;
        List<TodoItem> result = new java.util.ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            String id = String.valueOf(item.getOrDefault("id", String.valueOf(i + 1))).trim();
            String text = String.valueOf(item.getOrDefault("text", "")).trim();
            String status = String.valueOf(item.getOrDefault("status", "pending")).toLowerCase();
            if (StringUtils.isBlank(text)) {
                throw new IllegalArgumentException("Item " + id + ": text required");
            }
            if (!VALID_STATUSES.contains(status)) {
                throw new IllegalArgumentException("Item " + id + ": invalid status '" + status + "'");
            }
            if ("in_progress".equals(status)) {
                inProgressCount++;
            }
            result.add(new TodoItem(id, text, status));
        }
        if (inProgressCount > 1) {
            throw new IllegalArgumentException("Only one task can be in_progress at a time");
        }
        return result;
    }

    @Tool(description = "Read the current todo list for this conversation. Use this to check progress and see what tasks remain.")
    public String todoRead(ToolContext toolContext) {
        String conversationId = ConversationUtils.getToolsContext(toolContext).appId();
        List<TodoItem> items = TODOLIST_CACHE.getIfPresent(conversationId);
        return items == null || items.isEmpty() ? "No todos." : render(items);
    }

    private String render(List<TodoItem> items) {
        if (items == null || items.isEmpty()) {
            return "No todos.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n");
        for (TodoItem item : items) {
            String marker = STATUS_MARKERS.getOrDefault(item.status(), "[ ]");
            sb.append(marker).append(" #").append(item.id()).append(": ").append(item.text()).append("\n\n");
        }
        long done = items.stream().filter(t -> "completed".equals(t.status())).count();
        sb.append("\n(").append(done).append("/").append(items.size()).append(" completed)");
        return sb.append("\n\n").toString();
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
