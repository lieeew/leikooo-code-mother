package com.leikooo.codemother.service;

import com.leikooo.codemother.model.vo.UserVO;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * @author leikooo
 * @description SubAgent 自动修复服务接口
 * 实现 AI 修复 -> build -> validate -> 失败则重试 的循环
 */
public interface SubAgentService {

    /**
     * 执行修复循环
     * 
     * @param appId 应用 ID
     * @param user 当前登录用户
     * @return SSE 事件流
     */
    Flux<ServerSentEvent<String>> executeFixLoop(String appId, UserVO user);
}
