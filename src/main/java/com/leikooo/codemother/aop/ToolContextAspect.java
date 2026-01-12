package com.leikooo.codemother.aop;

import com.leikooo.codemother.ai.tools.ToolEventPublisher;
import com.leikooo.codemother.utils.ConversationIdUtils;
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

import java.util.Objects;

/**
 * @author leikooo
 */
@Aspect
@Component
@Slf4j
public class ToolContextAspect {

    private final ToolEventPublisher toolEventPublisher;

    public ToolContextAspect(ToolEventPublisher toolEventPublisher) {
        this.toolEventPublisher = toolEventPublisher;
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
        String sessionId = ConversationIdUtils.getConversationId(context);
        if (isBefore) {
            toolEventPublisher.publishToolCall(sessionId, className, methodName, toolCallId);
        } else {
            toolEventPublisher.publishToolResult(sessionId, className, methodName, toolCallId, result);
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