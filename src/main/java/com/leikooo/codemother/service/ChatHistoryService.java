package com.leikooo.codemother.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leikooo.codemother.model.dto.ChatHistoryQueryRequest;
import com.leikooo.codemother.model.entity.ChatHistory;
import com.baomidou.mybatisplus.extension.service.IService;
import com.leikooo.codemother.model.vo.UserVO;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
* @author leikooo
* @description 针对表【chat_history(对话历史)】的数据库操作Service
* @createDate 2026-02-06 20:26:11
*/
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加对话历史
     *
     * @param appId       应用 id
     * @param message     消息
     * @param messageType 消息类型
     * @param userId      用户 id
     * @return 是否成功
     */
    boolean addChatMessage(String appId, String message, String messageType, String userId);

    /**
     * 根据应用 id 删除对话历史
     *
     * @param appId
     * @return
     */
    boolean deleteByAppId(Long appId);

    /**
     * 分页查询某 APP 的对话记录
     *
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               String lastCreateTime,
                                               UserVO loginUser);

    /**
     * 构造查询条件
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    QueryWrapper<ChatHistory> getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
