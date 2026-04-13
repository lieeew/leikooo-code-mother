package com.leikooo.codemother.ai.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leikooo.codemother.model.dto.ToolsContext;
import com.leikooo.codemother.model.enums.AgentType;
import com.leikooo.codemother.service.TodoListRedisService;
import com.leikooo.codemother.utils.ConversationUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

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

    private final TodoListRedisService todoListRedisService;
    private final ObjectMapper objectMapper;

    public TodolistTools(TodoListRedisService todoListRedisService, ObjectMapper objectMapper) {
        this.todoListRedisService = todoListRedisService;
        this.objectMapper = objectMapper;
    }

    private String getAppId(ToolContext toolContext) {
        ToolsContext ctx = ConversationUtils.getToolsContext(toolContext);
        return ctx.appId();
    }

    private AgentType getAgentType(ToolContext toolContext) {
        ToolsContext ctx = ConversationUtils.getToolsContext(toolContext);
        return ctx.agentType();
    }

    @Tool(description = "Update task list. Track progress on multi-step tasks. Pass the full list of items each time (replaces previous list). " +
            "Each item must have id, text, status. Status: pending, in_progress, completed. Only one item can be in_progress."
    )
    public String todoUpdate(
            @ToolParam(description = "Full list of todo items. Each item: id (string), text (string), status (pending|in_progress|completed).")
            List<Map<String, Object>> items,
            ToolContext toolContext
    ) {
        try {
            String appId = getAppId(toolContext);
            AgentType agentType = getAgentType(toolContext);
            if (items.size() > MAX_TODOS) {
                return "Error: Max " + MAX_TODOS + " todos allowed";
            }
            List<TodoItem> validated = validateAndConvert(items);
            String jsonItems = objectMapper.writeValueAsString(validated);
            todoListRedisService.saveTodoList(appId, agentType, jsonItems);
            return render(validated);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        } catch (JsonProcessingException e) {
            return "Error: Failed to serialize todo list";
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
        String appId = getAppId(toolContext);
        AgentType agentType = getAgentType(toolContext);
        String jsonItems = todoListRedisService.getTodoList(appId, agentType);
        if (jsonItems == null || jsonItems.isEmpty()) {
            return "No todos.";
        }
        try {
            List<TodoItem> items = objectMapper.readValue(jsonItems, new TypeReference<List<TodoItem>>() {});
            return render(items);
        } catch (JsonProcessingException e) {
            return "No todos.";
        }
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

    /**
     * 清理指定 appId 和 agentType 的缓存
     */
    public void cleanup(String appId, AgentType agentType) {
        todoListRedisService.deleteTodoList(appId, agentType);
    }

    /**
     * 清理指定 appId 下所有 agentType 的缓存
     */
    public void cleanupAll(String appId) {
        todoListRedisService.deleteAllByAppId(appId);
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
