package com.leikooo.codemother.service.impl;

import com.leikooo.codemother.model.enums.AgentType;
import com.leikooo.codemother.service.TodoListRedisService;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * @author leikooo
 * @date 2025/12/31
 */
@Service
public class TodoListRedisServiceImpl implements TodoListRedisService {

    private static final String KEY_PREFIX = "todo:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final RedissonClient redissonClient;

    public TodoListRedisServiceImpl(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public void saveTodoList(String appId, AgentType agentType, String jsonItems) {
        String key = buildKey(appId, agentType);
        RBucket<String> bucket = redissonClient.getBucket(key);
        bucket.set(jsonItems, TTL);
    }

    @Override
    public String getTodoList(String appId, AgentType agentType) {
        String key = buildKey(appId, agentType);
        RBucket<String> bucket = redissonClient.getBucket(key);
        return bucket.get();
    }

    @Override
    public void deleteTodoList(String appId, AgentType agentType) {
        String key = buildKey(appId, agentType);
        redissonClient.getBucket(key).delete();
    }

    @Override
    public void deleteAllByAppId(String appId) {
        RKeys keys = redissonClient.getKeys();
        keys.deleteByPattern(KEY_PREFIX + appId);
    }

    private String buildKey(String appId, AgentType agentType) {
        return KEY_PREFIX + appId + ":" + agentType.name();
    }
}
