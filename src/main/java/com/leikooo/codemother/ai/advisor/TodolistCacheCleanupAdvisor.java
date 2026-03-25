package com.leikooo.codemother.ai.advisor;

import com.leikooo.codemother.ai.tools.TodolistTools;
import com.leikooo.codemother.model.dto.ChatContext;
import com.leikooo.codemother.utils.ConversationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * @author leikooo
 * @description TodolistCache 生命周期管理 Advisor
 *              在对话正常结束后自动清理对应的 TodolistCache
 */
@Slf4j
public class TodolistCacheCleanupAdvisor implements CallAdvisor, StreamAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        cleanupIfNeeded(request);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(request)
                .doOnComplete(() -> cleanupIfNeeded(request));
    }

    private void cleanupIfNeeded(ChatClientRequest request) {
        try {
            ChatContext ctx = ConversationUtils.getChatContext(request.context());
            if (ctx != null && ctx.agentType() != null) {
                TodolistTools.cleanup(ctx.appId(), ctx.agentType());
                log.debug("[TodolistCacheCleanup] Cleaned up cache for appId={}, agentType={}",
                        ctx.appId(), ctx.agentType());
            }
        } catch (Exception e) {
            log.warn("[TodolistCacheCleanup] Failed to cleanup cache", e);
        }
    }

    @Override
    public String getName() {
        return "TodolistCacheCleanupAdvisor";
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE + 200;
    }
}
