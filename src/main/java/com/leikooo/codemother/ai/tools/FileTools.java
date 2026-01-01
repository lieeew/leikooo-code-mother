package com.leikooo.codemother.ai.tools;

import com.leikooo.codemother.config.CodeGeneratorConfig;
import com.leikooo.codemother.utils.ConversationIdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author leikooo
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class FileTools extends BaseTools {

    private final CodeGeneratorConfig config;

    /**
     *  写入文件
     * @param relativeFilePath 路径
     * @param content 内容
     * @param toolContext toolContext 可以获取到 conversationId
     */
    @Tool(description = "Writes a file to the local filesystem.\n" +
            "\n" +
            "Usage:\n" +
            "- This tool will overwrite the existing file if there is one at the provided path.\n" +
            "- If this is an existing file, you MUST use the Read tool first to read the file's contents. This tool will fail if you did not read the file first.\n" +
            "- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.\n" +
            "- NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.\n" +
            "- Only use emojis if the user explicitly requests it. Avoid writing emojis to files unless asked."
    )
    public String writeFile(
            @ToolParam(description = "file relative path")
            String relativeFilePath,
            @ToolParam(description = "The content to write to the file")
            String content,
            ToolContext toolContext
    ) {
        try {
            String conversationId = ConversationIdUtils.getConversationId(toolContext);
            if (StringUtils.isEmpty(conversationId)) {
                throw new IllegalArgumentException("conversationId 不能为空");
            }
            if (StringUtils.isBlank(content)) {
                throw new IllegalArgumentException("文件内容不能为空");
            }
            if (StringUtils.isBlank(relativeFilePath)) {
                throw new IllegalArgumentException("文件路径不能为空");
            }
            String normalizedPath = normalizePath(relativeFilePath);
            Path outputDirPath = Paths.get(config.getOutputDir(), conversationId).toAbsolutePath().normalize();
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

            return "文件写入成功: " + normalizedPath;
        } catch (InvalidPathException e) {
            String errorMsg = "无效的文件路径: " + relativeFilePath;
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


    @Tool(description = "Reads a file from the local filesystem. You can access any file directly by using this tool.\n" +
            "Assume this tool is able to read all files on the machine. If the User provides a path to a file assume that path is valid. It is okay to read a file that does not exist; an error will be returned.\n" +
            "\n" +
            "Usage:\n" +
            "- The filePath parameter must be a relative path, not an absolute path \n" +
            "- By default, it reads up to 1000 lines starting from the beginning of the file\n" +
            "- You can optionally specify a line offset and limit (especially handy for long files), but it's recommended to read the whole file by not providing these parameters\n" +
            "- Any lines longer than 1000 characters will be truncated\n" +
            "- Results are returned using cat -n format, with line numbers starting at 1\n" +
            "- You have the capability to call multiple tools in a single response. It is always better to speculatively read multiple files as a batch that are potentially useful.\n" +
            "- If you read a file that exists but has empty contents you will receive a system reminder warning in place of file contents."
    )
    public String readFile(
            @ToolParam(description = "file relative path")
            String relativeFilePath,
            @ToolParam(description = "The number of lines to read (defaults to 2000)")
            Integer limit,
            @ToolParam(description = "The line number to start reading from (0-based)")
            Integer offset,
            ToolContext toolContext
    ) {
        try {
            String conversationId = ConversationIdUtils.getConversationId(toolContext);
            if (StringUtils.isBlank(relativeFilePath)) {
                throw new IllegalArgumentException("文件路径不能为空");
            }
            String normalizedPath = normalizePath(relativeFilePath);
            Path outputDirPath = Paths.get(config.getOutputDir(), conversationId).toAbsolutePath().normalize();
            Path filePath = outputDirPath.resolve(normalizedPath).normalize();

            if (!filePath.startsWith(outputDirPath)) {
                throw new SecurityException("禁止访问输出目录之外的路径");
            }
            if (!Files.exists(filePath)) {
                throw new IllegalArgumentException("文件不存在: " + normalizedPath);
            }
            StringBuilder result = new StringBuilder();
            result.append("文件路径: ").append(normalizedPath).append("\n\n");

            List<String> allLines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            int start = offset != null ? Math.max(0, offset) : 0;
            int lineLimit = limit != null ? Math.min(limit, 1000) : 1000;
            int end = Math.min(start + lineLimit, allLines.size());

            result.append("行数范围: L").append(start + 1).append("-L").append(end).append("\n\n");
            for (int i = start; i < end; i++) {
                result.append(String.format("%6d | %s%n", i + 1, allLines.get(i)));
            }

            if (end < allLines.size()) {
                result.append(String.format("%n... 还有 %d 行未显示 (总行数: %d)%n", allLines.size() - end, allLines.size()));
            }

            return result.toString();
        } catch (Exception e) {
            String errorMsg = "未知错误: " + e.getMessage();
            log.error(errorMsg, e);
            return "错误: " + errorMsg;
        }
    }

    @Tool(description = "Performs exact string replacements in files.\n" +
            "\n" +
            "Usage:\n" +
            "- You must use the Read tool at least once before editing. This tool will fail if you did not read the file first.\n" +
            "- When editing text from Read tool output, ensure you preserve the exact indentation (tabs/spaces).\n" +
            "- The edit will FAIL if `oldString` is not found in the file.\n" +
            "- The edit will FAIL if `oldString` is found multiple times - provide more context to make it unique.\n" +
            "- Use `replaceAll` for replacing all occurrences of a string."
    )
    public String editFile(
            @ToolParam(description = "file relative path")
            String relativeFilePath,
            @ToolParam(description = "The text to replace")
            String oldString,
            @ToolParam(description = "The text to replace it with (must be different from oldString)")
            String newString,
            @ToolParam(description = "Replace all occurrences of oldString (default: false)")
            Boolean replaceAll,
            ToolContext toolContext
    ) {
        try {
            String conversationId = ConversationIdUtils.getConversationId(toolContext);
            if (StringUtils.isBlank(relativeFilePath)) {
                throw new IllegalArgumentException("文件路径不能为空");
            }
            if (StringUtils.isBlank(oldString)) {
                throw new IllegalArgumentException("oldString 不能为空");
            }
            if (StringUtils.isBlank(newString)) {
                throw new IllegalArgumentException("newString 不能为空");
            }
            if (oldString.equals(newString)) {
                throw new IllegalArgumentException("newString 必须与 oldString 不同");
            }

            String normalizedPath = normalizePath(relativeFilePath);
            Path outputDirPath = Paths.get(config.getOutputDir(), conversationId).toAbsolutePath().normalize();
            Path filePath = outputDirPath.resolve(normalizedPath).normalize();

            if (!filePath.startsWith(outputDirPath)) {
                throw new SecurityException("禁止访问输出目录之外的路径");
            }
            if (!Files.exists(filePath)) {
                throw new IllegalArgumentException("文件不存在: " + normalizedPath);
            }

            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            String updatedContent;
            if (Boolean.TRUE.equals(replaceAll)) {
                if (!content.contains(oldString)) {
                    throw new IllegalArgumentException("oldString not found in content");
                }
                updatedContent = content.replace(oldString, newString);
            } else {
                if (!content.contains(oldString)) {
                    throw new IllegalArgumentException("oldString not found in content");
                }
                int count = (content.length() - content.replace(oldString, "").length()) / oldString.length();
                if (count > 1) {
                    throw new IllegalArgumentException("oldString found " + count + " times - use replaceAll or provide more context");
                }
                updatedContent = content.replace(oldString, newString);
            }

            Files.writeString(filePath, updatedContent, StandardCharsets.UTF_8);
            log.info("文件编辑成功: {}", filePath);
            return "文件编辑成功: " + normalizedPath;
        } catch (InvalidPathException e) {
            String errorMsg = "无效的文件路径: " + relativeFilePath;
            log.error(errorMsg, e);
            return "错误: " + errorMsg;
        } catch (IllegalArgumentException e) {
            log.error("参数错误: {}", e.getMessage());
            return "错误: " + e.getMessage();
        } catch (SecurityException e) {
            log.error("安全错误: {}", e.getMessage());
            return "错误: " + e.getMessage();
        } catch (IOException e) {
            String errorMsg = "文件编辑失败: " + e.getMessage();
            log.error(errorMsg, e);
            return "错误: " + errorMsg;
        } catch (Exception e) {
            String errorMsg = "未知错误: " + e.getMessage();
            log.error(errorMsg, e);
            return "错误: " + errorMsg;
        }
    }

    private static final Set<String> CODE_EXTENSIONS = new HashSet<>(Set.of(
            ".java", ".html", ".css", ".js", ".ts", ".py", ".go", ".rs", ".cpp",
            ".c", ".h", ".json", ".xml", ".yaml", ".yml", ".md", ".sql", ".vue"
    ));

    @Tool(description = "Lists files and directories in a given path. The path parameter must be relative path; ")
    public String listFiles(
            @ToolParam(description = "file relative path")
            String relativeFilePath,
            ToolContext toolContext
    ) {
        try {
            String conversationId = ConversationIdUtils.getConversationId(toolContext);
            Path outputDirPath = Paths.get(config.getOutputDir(), conversationId).toAbsolutePath().normalize();

            Path targetDir;
            if (StringUtils.isBlank(relativeFilePath)) {
                targetDir = outputDirPath;
            } else {
                String normalizedPath = normalizePath(relativeFilePath);
                targetDir = outputDirPath.resolve(normalizedPath).normalize();
            }

            if (!targetDir.startsWith(outputDirPath)) {
                throw new SecurityException("禁止访问输出目录之外的路径");
            }
            if (!Files.exists(targetDir)) {
                String displayPath = StringUtils.isBlank(relativeFilePath) ? "" : normalizePath(relativeFilePath);
                throw new IllegalArgumentException("目录不存在: " + displayPath);
            }
            if (!Files.isDirectory(targetDir)) {
                String displayPath = StringUtils.isBlank(relativeFilePath) ? "" : normalizePath(relativeFilePath);
                throw new IllegalArgumentException("路径不是目录: " + displayPath);
            }

            StringBuilder result = new StringBuilder();
            List<Path> codeFiles = Files.walk(targetDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String ext = getFileExtension(path.getFileName().toString());
                        return CODE_EXTENSIONS.contains(ext);
                    })
                    .toList();

            result.append("代码文件列表 (").append(codeFiles.size()).append(" 个):\n");
            for (int i = 0; i < codeFiles.size(); i++) {
                Path filePath = codeFiles.get(i);
                String relative = outputDirPath.relativize(filePath).toString();
                result.append(String.format("%3d. %s%n", i + 1, relative));
            }

            return result.toString();
        } catch (InvalidPathException e) {
            String errorMsg = "无效的路径: " + relativeFilePath;
            log.error(errorMsg, e);
            return "错误: " + errorMsg;
        } catch (IllegalArgumentException e) {
            log.error("参数错误: {}", e.getMessage());
            return "错误: " + e.getMessage();
        } catch (SecurityException e) {
            log.error("安全错误: {}", e.getMessage());
            return "错误: " + e.getMessage();
        } catch (IOException e) {
            String errorMsg = "列出文件失败: " + e.getMessage();
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
        return "文件工具";
    }

    @Override
    String getToolDes() {
        return "调用工具的 Tools 的集合";
    }
}