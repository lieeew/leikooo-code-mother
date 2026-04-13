package com.leikooo.codemother.service;

import com.leikooo.codemother.model.enums.AgentType;

/**
 * @author leikooo
 * @date 2025/12/31
 */
public interface TodoListRedisService {

    /**
     * 保存 Todo 列表到 Redis
     *
     * @param appId      应用 ID
     * @param agentType  代理类型
     * @param jsonItems  JSON 格式的 todo 列表
     */
    void saveTodoList(String appId, AgentType agentType, String jsonItems);

    /**
     * 获取 Todo 列表
     *
     * @param appId     应用 ID
     * @param agentType 代理类型
     * @return JSON 格式的 todo 列表，未找到返回 null
     */
    String getTodoList(String appId, AgentType agentType);

    /**
     * 删除指定 appId 和 agentType 的 Todo 列表
     *
     * @param appId     应用 ID
     * @param agentType 代理类型
     */
    void deleteTodoList(String appId, AgentType agentType);

    /**
     * 删除指定 appId 下所有 agentType 的 Todo 列表
     *
     * @param appId 应用 ID
     */
    void deleteAllByAppId(String appId);
}
