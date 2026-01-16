package com.leikooo.codemother.service.impl;

import com.leikooo.codemother.model.entity.SpringAiChatMemory;
import com.leikooo.codemother.service.SpringAiChatMemoryService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/1/15
 * @description
 */
@SpringBootTest
class SpringAiChatMemoryServiceImplTest {

    @Resource
    private SpringAiChatMemoryService springAiChatMemoryService;

    @Test
    public void test() {
        String appId = "2011785452679561217";
        SpringAiChatMemory existingRecord = springAiChatMemoryService.lambdaQuery()
                .eq(SpringAiChatMemory::getConversationId, appId)
                .eq(SpringAiChatMemory::getType, MessageType.ASSISTANT)
                .orderByDesc(SpringAiChatMemory::getTimestamp)
                .one();
        existingRecord.setContent("\n" +
                "\n" +
                "[选择工具] 正在进行工具调用 todoWrite:  \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 工具调用完成 todoWrite: Todo list saved successfully.\n" +
                "\n" +
                "Current todo list:\n" +
                "1. [>] 创建带错误的最简Vue项目\n" +
                "   - package.json\n" +
                "   - vite.config.js\n" +
                "   - index.html\n" +
                "   - src/main.js\n" +
                "   - src/App.vue (包含错误) \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 正在进行工具调用 writeFile:  \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 工具调用完成 writeFile: 文件写入成功: package.json \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 正在进行工具调用 writeFile:  \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 工具调用完成 writeFile: 文件写入成功: vite.config.js \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 正在进行工具调用 writeFile:  \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 工具调用完成 writeFile: 文件写入成功: index.html \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 正在进行工具调用 writeFile:  \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 工具调用完成 writeFile: 文件写入成功: src/main.js \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 正在进行工具调用 writeFile:  \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 工具调用完成 writeFile: 文件写入成功: src/App.vue \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 正在进行工具调用 todoWrite:  \n" +
                "\n" +
                "\n" +
                "\n" +
                "[选择工具] 工具调用完成 todoWrite: Todo list saved successfully.\n" +
                "\n" +
                "Current todo list:\n" +
                "1. [x] 创建带错误的最简Vue项目\n" +
                "   - package.json ✓\n" +
                "   - vite.config.js ✓\n" +
                "   - index.html ✓\n" +
                "   - src/main.js ✓\n" +
                "   - src/App.vue (包含错误) ✓ \n" +
                "\n" +
                "已创建带错误的 Vue 项目。\n" +
                "\n" +
                "**包含的错误：**\n" +
                "1. `undefinedFunction()` - 调用未定义的函数\n" +
                "2. `unknownVariable` - 使用未定义的变量\n" +
                "3. `<UndefinedComponent />` - 引用未注册的组件\n" +
                "\n" +
                "运行 `npm run build` 会报错，你可以用来验证修复流程。");
        existingRecord.setTimestamp(new Date());

        SpringAiChatMemory newRecord = SpringAiChatMemory.builder()
                .conversationId(appId)
                .type(MessageType.ASSISTANT)
                .content(existingRecord.getContent())
                .timestamp(new Date())
                .build();
        boolean save = springAiChatMemoryService.save(newRecord);
        System.out.println("save = " + save);
    }

}