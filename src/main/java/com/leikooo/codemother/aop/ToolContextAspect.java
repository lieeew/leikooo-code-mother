package com.leikooo.codemother.aop;

import com.leikooo.codemother.ai.tools.ToolEventPublisher;
import com.leikooo.codemother.model.entity.ToolCallRecord;
import com.leikooo.codemother.model.enums.ToolCallTypeEnum;
import com.leikooo.codemother.service.ToolCallRecordService;
import com.leikooo.codemother.utils.ConversationUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Objects;

/**
 * @author leikooo
 */
@Aspect
@Component
@Slf4j
public class ToolContextAspect {

    private final ToolEventPublisher toolEventPublisher;
    private final ToolCallRecordService toolCallRecordService;

    public ToolContextAspect(ToolEventPublisher toolEventPublisher, ToolCallRecordService toolCallRecordService) {
        this.toolEventPublisher = toolEventPublisher;
        this.toolCallRecordService = toolCallRecordService;
    }

    @Pointcut("execution(* com.leikooo.codemother.ai.tools..*.*(..)) && @annotation(org.springframework.ai.tool.annotation.Tool)")
    public void anyToolExecution() {

    }

    @Before("anyToolExecution()")
    public void beforeToolCall(JoinPoint joinPoint) {
        ToolContext toolContext = getToolContext(joinPoint);
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        if (Objects.isNull(toolContext)) {
            log.warn("SKIPPED: Tool method [{}.{}] was called but does not accept ToolContext as a parameter.",
                    className, methodName);
            return;
        }
        handleToolContext(toolContext, className, methodName, null, true);
    }

    @AfterReturning(pointcut = "anyToolExecution()", returning = "result")
    public void afterToolCall(JoinPoint joinPoint, Object result) {
        ToolContext toolContext = getToolContext(joinPoint);
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        if (toolContext != null) {
            handleToolContext(toolContext, className, methodName, result, false);
        }
    }

    private void handleToolContext(ToolContext context, String className, String methodName, Object result, boolean isBefore) {
        Message message = context.getToolCallHistory().getLast();
        AssistantMessage.ToolCall toolCallInfo = ((AssistantMessage) message).getToolCalls().getLast();
        String toolCallId = toolCallInfo.id();
        String sessionId = ConversationUtils.getToolsContext(context).appId();
        if (isBefore) {
            toolEventPublisher.publishToolCall(sessionId, className, methodName, toolCallId);
            saveToolRecord(sessionId, toolCallId, className, methodName, ToolCallTypeEnum.CALL, null);
        } else {
            toolEventPublisher.publishToolResult(sessionId, className, methodName, toolCallId, result);
            saveToolRecord(sessionId, toolCallId, className, methodName, ToolCallTypeEnum.RESULT, String.valueOf(result));
        }
    }

    private void saveToolRecord(String sessionId, String toolCallId, String className, String methodName, ToolCallTypeEnum callType, String result) {
        try {
            ToolCallRecord record = ToolCallRecord.builder()
                    .sessionId(sessionId)
                    .toolCallId(toolCallId)
                    .className(className)
                    .methodName(methodName)
                    .callType(callType.getValue())
                    .result(result)
                    .createTime(new Date())
                    .build();
            toolCallRecordService.save(record);
        } catch (Exception e) {
            log.error("保存 tool call 记录失败: {}", e.getMessage());
        }
    }

    /**
     * toolContext
     * @param joinPoint joinPoint
     * @return ToolContext
     */
    private ToolContext getToolContext(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        ToolContext toolContext = null;
        for (Object arg : args) {
            if (arg instanceof ToolContext) {
                toolContext = (ToolContext) arg;
                break;
            }
        }
        return toolContext;
    }
}