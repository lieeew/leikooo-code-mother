package com.leikooo.codemother.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leikooo.codemother.annotation.AuthCheck;
import com.leikooo.codemother.commen.BaseResponse;
import com.leikooo.codemother.commen.ResultUtils;
import com.leikooo.codemother.constant.UserConstant;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.model.dto.ChatHistoryQueryRequest;
import com.leikooo.codemother.model.entity.SpringAiChatMemory;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.SpringAiChatMemoryService;
import com.leikooo.codemother.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/1/13
 * @description
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {
    private final SpringAiChatMemoryService springAiChatMemoryService;
    private final UserService userService;

    public ChatHistoryController(SpringAiChatMemoryService springAiChatMemoryService, UserService userService) {
        this.springAiChatMemoryService = springAiChatMemoryService;
        this.userService = userService;
    }

    /**
     * 分页查询某个应用的对话历史（游标查询）
     *
     * @param appId          应用ID
     * @param pageSize       页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<SpringAiChatMemory>> listAppChatHistory(
            @PathVariable(name = "appId") Long appId,
            @RequestParam(defaultValue = "10", name = "pageSize") int pageSize,
            @RequestParam(required = false, name = "lastCreateTime")
            LocalDateTime lastCreateTime
    ) {
        UserVO loginUser = userService.getUserLogin();
        ThrowUtils.throwIf(pageSize > 50, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(Objects.isNull(loginUser), ErrorCode.PARAMS_ERROR, "appId 不能为空");
        Page<SpringAiChatMemory> result = springAiChatMemoryService.listAppChatHistoryByPage(appId, pageSize, lastCreateTime, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 管理员分页查询所有对话历史
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 对话历史分页
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<SpringAiChatMemory>> listAllChatHistoryByPageForAdmin(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = chatHistoryQueryRequest.getCurrent();
        long pageSize = chatHistoryQueryRequest.getPageSize();
        // 查询数据
        QueryWrapper<SpringAiChatMemory> queryWrapper = springAiChatMemoryService.getQueryWrapper(chatHistoryQueryRequest);
        Page<SpringAiChatMemory> result = springAiChatMemoryService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(result);
    }
}
