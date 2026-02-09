package com.leikooo.codemother.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leikooo.codemother.constant.UserConstant;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.mapper.ChatHistoryMapper;
import com.leikooo.codemother.model.dto.ChatHistoryQueryRequest;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.entity.ChatHistory;
import com.leikooo.codemother.model.enums.ChatHistoryMessageTypeEnum;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.ChatHistoryService;
import com.leikooo.codemother.utils.UuidV7Generator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * @author leikooo
 */
@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    private final AppService appService;

    public ChatHistoryServiceImpl(@Lazy AppService appService) {
        this.appService = appService;
    }

    @Override
    public boolean addChatMessage(String appId, String message, String messageType, String userId) {
        // 基础校验
        ThrowUtils.throwIf(StrUtil.isBlank(appId), ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(userId), ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        // 验证消息类型是否有效
        ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的消息类型");
        // 插入数据库
        ChatHistory chatHistory = ChatHistory.builder()
                .appId(Long.parseLong(appId))
                .message(message)
                .messageType(messageType)
                .userId(UuidV7Generator.stringToBytes(userId))
                .build();
        return this.save(chatHistory);
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        QueryWrapper<ChatHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("appId", appId);
        return this.remove(queryWrapper);
    }

    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                                      String lastCreateTime,
                                                      UserVO loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = UuidV7Generator.bytesToUuid(app.getUserId()).equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        LocalDateTime lastCreate = null;
        if (lastCreateTime != null && !lastCreateTime.isEmpty()) {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(lastCreateTime);
            lastCreate = offsetDateTime.toLocalDateTime().plusHours(8);
        }
        queryRequest.setLastCreateTime(lastCreate);
        QueryWrapper<ChatHistory> queryWrapper = this.getQueryWrapper(queryRequest);
        // 查询数据
        return this.page(Page.of(1, pageSize), queryWrapper);
    }


    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<ChatHistory> getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper<ChatHistory> queryWrapper = new QueryWrapper<>();
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
                .eq(Objects.nonNull(appId), "appId", appId);
        // 游标查询逻辑
        if (Objects.nonNull(lastCreateTime)) {
            queryWrapper.lt(true, "createTime", lastCreateTime);
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(true, "ascend".equals(sortOrder), sortField);
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy(true, false, "createTime");
        }
        return queryWrapper;
    }
}
