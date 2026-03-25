package com.leikooo.codemother.model.dto;

import com.leikooo.codemother.model.enums.AgentType;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/2/6
 * @description
 */
public record ToolsContext(
        String appId,
        String userId,
        AgentType agentType
) {
    // 主 Agent 默认使用 MAIN
    public static ToolsContext of(String appId, String userId) {
        return new ToolsContext(appId, userId, AgentType.MAIN);
    }

    // SubAgent 专用
    public static ToolsContext subAgent(String appId, String userId) {
        return new ToolsContext(appId, userId, AgentType.SUB_AGENT);
    }
}
