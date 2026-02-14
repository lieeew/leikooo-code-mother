package com.leikooo.codemother.ai.tools;

import com.leikooo.codemother.utils.ConversationUtils;
import com.leikooo.codemother.utils.TokenEstimator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @description 上下文相关的 tools
 */
@Slf4j
@Component
public class ContextTools extends BaseTools {

    private static final int MAX_CONTEXT_TOKENS = 60_000;

    private final JdbcChatMemoryRepository chatMemoryRepository;

    public ContextTools(JdbcChatMemoryRepository chatMemoryRepository) {
        this.chatMemoryRepository = chatMemoryRepository;
    }

    @Tool(description = "Check the current context window usage. Returns token count, percentage used, and compression status. Use this when you want to understand how much context space remains.")
    public String checkContextUsage(ToolContext toolContext) {
        String conversationId = ConversationUtils.getToolsContext(toolContext).appId();
        List<Message> messages = chatMemoryRepository.findByConversationId(conversationId);
        int totalTokens = TokenEstimator.estimateTokens(messages);
        double usagePercent = (totalTokens * 100.0) / MAX_CONTEXT_TOKENS;
        boolean compressionNeeded = totalTokens > (int) (MAX_CONTEXT_TOKENS * 0.5);

        return String.format(
                "Context Usage:\n- Messages: %d\n- Estimated tokens: %d / %d\n- Usage: %.1f%%\n- Compression threshold: 50%%\n- Status: %s",
                messages.size(), totalTokens, MAX_CONTEXT_TOKENS, usagePercent,
                compressionNeeded ? "COMPRESSION WILL TRIGGER on next request" : "OK"
        );
    }

    @Override
    String getToolName() {
        return "Context Tools";
    }

    @Override
    String getToolDes() {
        return "Context window monitoring tools";
    }
}
