package com.leikooo.codemother.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.mapper.SpringAiChatMemoryMapper;
import com.leikooo.codemother.model.dto.ChatHistoryQueryRequest;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.entity.SpringAiChatMemory;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.SpringAiChatMemoryService;
import com.leikooo.codemother.service.UserService;
import com.leikooo.codemother.utils.UuidV7Generator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * @author leikooo
 * @description 针对表【spring_ai_chat_memory】的数据库操作Service实现
 * @createDate 2026-01-13 14:54:31
 */
@Service
public class SpringAiChatMemoryServiceImpl extends ServiceImpl<SpringAiChatMemoryMapper, SpringAiChatMemory>
        implements SpringAiChatMemoryService {

    private final AppService appService;
    private final UserService userService;

    public SpringAiChatMemoryServiceImpl(AppService appService, UserService userService) {
        this.appService = appService;
        this.userService = userService;
    }

    @Override
    public Page<SpringAiChatMemory> listAppChatHistoryByPage(
            Long appId, int pageSize,
            LocalDateTime lastCreateTime,
            UserVO loginUser) {
        ThrowUtils.throwIf(pageSize > 50, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(Objects.isNull(appId) || Objects.isNull(loginUser), ErrorCode.PARAMS_ERROR);
        App app = appService.getById(appId);
        String appUserId = Optional.ofNullable(app).map(App::getUserId).map(UuidV7Generator::bytesToUuid).orElseThrow();
        String loginUserId = loginUser.getId();
        ThrowUtils.throwIf(!loginUserId.equals(appUserId) && !userService.isAdmin(), ErrorCode.NO_AUTH_ERROR);
        // 构建查询条件
        ChatHistoryQueryRequest chatHistoryQueryRequest = new ChatHistoryQueryRequest();
        chatHistoryQueryRequest.setAppId(appId);
        chatHistoryQueryRequest.setLastCreateTime(lastCreateTime);
        // 查询数据
        return this.page(Page.of(1, pageSize), this.getQueryWrapper(chatHistoryQueryRequest));
    }

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest chatHistoryQueryRequest
     * @return queryRequest
     */
    @Override
    public QueryWrapper<SpringAiChatMemory> getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper<SpringAiChatMemory> queryWrapper = new QueryWrapper<>();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper
                .like(StringUtils.isNotBlank(message), "message", message)
                .eq(StringUtils.isNotBlank(messageType), "messageType", messageType)
                .eq(Objects.nonNull(appId), "conversation_id", appId);
        // 游标查询逻辑 - 只使用 createTime 作为游标
        queryWrapper.lt(Objects.nonNull(lastCreateTime), "timestamp", lastCreateTime);
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(true, "ascend".equals(sortOrder), sortField);
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy(true, false, "timestamp");
        }
        return queryWrapper;
    }
}




