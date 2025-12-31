package com.leikooo.codemother.ai.tools;

import com.leikooo.codemother.config.CodeGeneratorConfig;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author leikooo
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class FileTools extends BaseTools{

    private final CodeGeneratorConfig config;

    @Tool(name = "写入文件到指定目录")
    public String write(
            @P(value = "需要写入的文件内容")
            String content,
            @P(value = "需要写入文件相对路径")
            String path,
            @ToolMemoryId
            String memoryId
    ) {
        try {
            if (StringUtils.isBlank(content)) {
                throw new IllegalArgumentException("文件内容不能为空");
            }
            if (StringUtils.isBlank(path)) {
                throw new IllegalArgumentException("文件路径不能为空");
            }
            String normalizedPath = normalizePath(path);
            String extension = getFileExtension(normalizedPath);
            Path outputDirPath = Paths.get(config.getOutputDir()).toAbsolutePath().normalize();
            Path filePath = outputDirPath.resolve(normalizedPath).normalize();

            if (!filePath.startsWith(outputDirPath)) {
                throw new SecurityException("禁止访问输出目录之外的路径");
            }
            if (content.getBytes(StandardCharsets.UTF_8).length > config.getMaxFileSize()) {
                throw new IllegalArgumentException("文件大小超过限制: " + config.getMaxFileSize() + " 字节");
            }
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.info("创建目录: {}", parentDir);
            }
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            log.info("文件写入成功: {}, 大小: {} 字节", filePath, content.length());

            return "文件写入成功: " + filePath.toUri();
        } catch (InvalidPathException e) {
            String errorMsg = "无效的文件路径: " + path;
            log.error(errorMsg, e);
            return "错误: " + errorMsg;
        } catch (IllegalArgumentException e) {
            log.error("参数错误: {}", e.getMessage());
            return "错误: " + e.getMessage();
        } catch (SecurityException e) {
            log.error("安全错误: {}", e.getMessage());
            return "错误: " + e.getMessage();
        } catch (IOException e) {
            String errorMsg = "文件写入失败: " + e.getMessage();
            log.error(errorMsg, e);
            return "错误: " + errorMsg;
        } catch (Exception e) {
            String errorMsg = "未知错误: " + e.getMessage();
            log.error(errorMsg, e);
            return "错误: " + errorMsg;
        }
    }

    private String normalizePath(String path) {
        path = path.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot).toLowerCase();
        }
        return "";
    }

    @Override
    String getToolName() {
        return "写入文件工具";
    }

    @Override
    String getToolDes() {
        return "写入文件的工具";
    }
}
