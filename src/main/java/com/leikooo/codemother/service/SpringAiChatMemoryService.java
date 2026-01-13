package com.leikooo.codemother.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.leikooo.codemother.model.dto.ChatHistoryQueryRequest;
import com.leikooo.codemother.model.entity.SpringAiChatMemory;
import com.leikooo.codemother.model.vo.UserVO;

import java.time.LocalDateTime;

/**
* @author leikooo
* @description 针对表【spring_ai_chat_memory】的数据库操作Service
* @createDate 2026-01-13 14:54:31
*/
public interface SpringAiChatMemoryService extends IService<SpringAiChatMemory> {

    /**
     * 分页查询应用聊天历史记录
     * @param appId 应用ID
     * @param pageSize 每页大小
     * @param lastCreateTime 游标时间，查询此时间之前的数据（用于实现游标分页）
     * @param loginUser 当前登录用户
     * @return 聊天历史分页结果
     */
    Page<SpringAiChatMemory> listAppChatHistoryByPage(Long appId, int pageSize, LocalDateTime lastCreateTime, UserVO loginUser);

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest chatHistoryQueryRequest
     * @return queryRequest
     */
    QueryWrapper<SpringAiChatMemory> getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
