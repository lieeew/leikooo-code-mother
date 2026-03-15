package com.leikooo.codemother.ai.tools;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.leikooo.codemother.config.CodeGeneratorConfig;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.enums.CodeGenTypeEnum;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.utils.ConversationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author leikooo
 */
@Slf4j
@Component
public class FileTools extends BaseTools {

    private final CodeGeneratorConfig config;
    private final AppService appService;

    private static final Cache<String, String> GEN_TYPE_CACHE = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    private static final String HTML_EXTENSION = ".html";
    private static final String INDEX_HTML = "index.html";

    public FileTools(CodeGeneratorConfig config, @Lazy AppService appService) {
        this.config = config;
        this.appService = appService;
    }

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
            String conversationId = ConversationUtils.getToolsContext(toolContext).appId();
            if (StringUtils.isEmpty(conversationId)) {
                throw new IllegalArgumentException("conversationId 不能为空");
            }
            if (StringUtils.isBlank(content)) {
                throw new IllegalArgumentException("文件内容不能为空");
            }
            if (StringUtils.isBlank(relativeFilePath)) {
                throw new IllegalArgumentException("文件路径不能为空");
            }
            String fileExtension = getFileExtension(relativeFilePath);
            if (HTML_EXTENSION.equals(fileExtension)) {
                String genType = getGenTypeWithCache(conversationId);
                if (CodeGenTypeEnum.HTML.getValue().equals(genType) || CodeGenTypeEnum.MULTI_FILE.getValue().equals(genType)) {
                    relativeFilePath = INDEX_HTML;
                }
            }
            String normalizedPath = normalizePath(relativeFilePath);
            Path outputDirPath = Paths.get(config.getOutputDir(), conversationId, "current").toAbsolutePath().normalize();
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
            String conversationId = ConversationUtils.getToolsContext(toolContext).appId();
            if (StringUtils.isBlank(relativeFilePath)) {
                throw new IllegalArgumentException("文件路径不能为空");
            }
            String normalizedPath = normalizePath(relativeFilePath);
            Path outputDirPath = Paths.get(config.getOutputDir(), conversationId, "current").toAbsolutePath().normalize();
            Path filePath = outputDirPath.resolve(normalizedPath).normalize();

            if (!filePath.startsWith(outputDirPath)) {
                throw new SecurityException("禁止访问输出目录之外的路径");
            }
            if (!Files.exists(filePath)) {
                throw new IllegalArgumentException("文件不存在: " + normalizedPath);
            }
            StringBuilder result = new StringBuilder();
            result.append("文件路径: ").append(normalizedPath).append("\n");

            List<String> allLines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            int start = offset != null ? Math.max(0, offset) : 0;
            int lineLimit = limit != null ? Math.min(limit, 1000) : 1000;
            int end = Math.min(start + lineLimit, allLines.size());

            result.append("行数范围: L").append(start + 1).append("-L").append(end).append("\n\n");
            result.append("```").append(getFileExtension(normalizedPath)).append("\n");
            for (int i = start; i < end; i++) {
                result.append(allLines.get(i)).append("\n");
            }
            result.append("```");

            if (end < allLines.size()) {
                result.append(String.format(
                    "%n... 还有 %d 行未显示 (总行数: %d)，使用 offset=%d 继续读取%n",
                    allLines.size() - end, allLines.size(), end));
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
            String conversationId = ConversationUtils.getToolsContext(toolContext).appId();
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
            Path outputDirPath = Paths.get(config.getOutputDir(), conversationId, "current").toAbsolutePath().normalize();
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

    private static final Set<String> SKIP_DIRS = Set.of("node_modules", "dist");

    @Tool(description = "Lists files and directories in a given path. The path parameter must be relative path; ")
    public String listFiles(
            @ToolParam(description = "file relative path")
            String relativeFilePath,
            ToolContext toolContext
    ) {
        try {
            String conversationId = ConversationUtils.getToolsContext(toolContext).appId();
            Path outputDirPath = Paths.get(config.getOutputDir(), conversationId, "current").toAbsolutePath().normalize();

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
            List<Path> codeFiles = new ArrayList<>();
            Files.walkFileTree(targetDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (SKIP_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String ext = getFileExtension(file.getFileName().toString());
                    if (CODE_EXTENSIONS.contains(ext)) {
                        codeFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

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

    private static final int GREP_MAX_MATCHES = 200;
    private static final int GREP_MAX_LINE_LENGTH = 500;

    private Pattern compilePattern(String pattern) {
        return Pattern.compile(pattern);
    }

    private Path resolveSearchRoot(String conversationId, String directory) {
        Path outputDirPath = Paths.get(config.getOutputDir(), conversationId, "current").toAbsolutePath().normalize();
        if (StringUtils.isBlank(directory)) {
            return outputDirPath;
        }
        Path searchRoot = outputDirPath.resolve(normalizePath(directory)).normalize();
        if (!searchRoot.startsWith(outputDirPath)) {
            throw new SecurityException("禁止访问输出目录之外的路径");
        }
        return searchRoot;
    }

    private List<String> collectGrepMatches(Path searchRoot, Pattern regex, String fileExtension) throws IOException {
        List<String> matches = new ArrayList<>();
        Path baseDir = searchRoot;
        Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (SKIP_DIRS.contains(dir.getFileName() != null ? dir.getFileName().toString() : "")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (matches.size() >= GREP_MAX_MATCHES) {
                    return FileVisitResult.TERMINATE;
                }
                if (!isSearchableFile(file, fileExtension)) {
                    return FileVisitResult.CONTINUE;
                }
                searchFileForPattern(file, baseDir, regex, matches);
                return FileVisitResult.CONTINUE;
            }
        });
        return matches;
    }

    private boolean isSearchableFile(Path file, String fileExtension) {
        String ext = getFileExtension(file.getFileName().toString());
        if (!CODE_EXTENSIONS.contains(ext)) {
            return false;
        }
        return StringUtils.isBlank(fileExtension) || ext.equals(fileExtension.startsWith(".") ? fileExtension : "." + fileExtension);
    }

    private void searchFileForPattern(Path file, Path baseDir, Pattern regex, List<String> matches) throws IOException {
        String relativePath = baseDir.relativize(file).toString();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null && matches.size() < GREP_MAX_MATCHES) {
                lineNum++;
                if (regex.matcher(line).find()) {
                    String truncated = line.length() > GREP_MAX_LINE_LENGTH ? line.substring(0, GREP_MAX_LINE_LENGTH) + "..." : line;
                    matches.add(String.format("%s:%d: %s", relativePath, lineNum, truncated));
                }
            }
        }
    }

    private String formatGrepResult(String pattern, List<String> matches) {
        if (matches.isEmpty()) {
            return "未找到匹配: " + pattern;
        }
        StringBuilder result = new StringBuilder();
        result.append(String.format("搜索 \"%s\" 找到 %d 个匹配:\n\n", pattern, matches.size()));
        matches.forEach(m -> result.append(m).append("\n"));
        if (matches.size() >= GREP_MAX_MATCHES) {
            result.append("\n... 结果已截断，最多显示 ").append(GREP_MAX_MATCHES).append(" 个匹配");
        }
        return result.toString();
    }

    @Tool(description = "Search for files by name pattern (glob). " +
            "Returns file paths matching the given glob pattern.\n\n" +
            "Usage:\n" +
            "- Pattern examples: '*.vue', '**/*Controller.java', 'src/**/*.ts'\n" +
            "- Uses Java glob syntax (*, **, ?, [abc])\n" +
            "- Results are capped at 500 files")
    public String glob(
            @ToolParam(description = "Glob pattern to match file names, e.g. '*.vue', '**/*.js'")
            String pattern,
            @ToolParam(description = "Optional: subdirectory to limit search scope (relative path)")
            String directory,
            ToolContext toolContext
    ) {
        try {
            String conversationId = ConversationUtils.getToolsContext(toolContext).appId();
            if (StringUtils.isBlank(pattern)) {
                throw new IllegalArgumentException("glob 模式不能为空");
            }
            Path searchRoot = resolveSearchRoot(conversationId, directory);
            List<String> matched = collectGlobMatches(searchRoot, pattern);
            return formatGlobResult(pattern, matched);
        } catch (Exception e) {
            log.error("glob 失败: {}", e.getMessage(), e);
            return "错误: " + e.getMessage();
        }
    }

    private List<String> collectGlobMatches(Path searchRoot, String pattern) throws IOException {
        String globPattern = pattern.contains("/") || pattern.contains("**") ? pattern : "**/" + pattern;
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
        List<String> matched = new ArrayList<>();
        Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (SKIP_DIRS.contains(dir.getFileName() != null ? dir.getFileName().toString() : "")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path relative = searchRoot.relativize(file);
                if (matcher.matches(relative) && matched.size() < 500) {
                    matched.add(relative.toString());
                }
                return matched.size() >= 500 ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
        });
        return matched;
    }

    private String formatGlobResult(String pattern, List<String> matched) {
        if (matched.isEmpty()) {
            return "未找到匹配文件: " + pattern;
        }
        StringBuilder result = new StringBuilder();
        result.append(String.format("匹配 \"%s\" 找到 %d 个文件:\n\n", pattern, matched.size()));
        for (int i = 0; i < matched.size(); i++) {
            result.append(String.format("%3d. %s%n", i + 1, matched.get(i)));
        }
        if (matched.size() >= 500) {
            result.append("\n... 结果已截断，最多显示 500 个文件");
        }
        return result.toString();
    }

    @Tool(description = "Delete a file at the specified relative path.\n\n" +
            "Usage:\n" +
            "- The path must be a relative path\n" +
            "- Only files within the project directory can be deleted\n" +
            "- Directories cannot be deleted with this tool")
    public String deleteFile(
            @ToolParam(description = "file relative path to delete")
            String relativeFilePath,
            ToolContext toolContext
    ) {
        try {
            String conversationId = ConversationUtils.getToolsContext(toolContext).appId();
            if (StringUtils.isBlank(relativeFilePath)) {
                throw new IllegalArgumentException("文件路径不能为空");
            }
            Path filePath = resolveAndValidatePath(conversationId, relativeFilePath);
            if (!Files.exists(filePath)) {
                return "文件不存在: " + normalizePath(relativeFilePath);
            }
            if (Files.isDirectory(filePath)) {
                return "错误: 不能删除目录，请指定文件路径";
            }
            Files.delete(filePath);
            log.info("文件删除成功: {}", filePath);
            return "文件删除成功: " + normalizePath(relativeFilePath);
        } catch (Exception e) {
            log.error("文件删除失败: {}", e.getMessage(), e);
            return "错误: " + e.getMessage();
        }
    }

    private Path resolveAndValidatePath(String conversationId, String relativeFilePath) {
        String normalizedPath = normalizePath(relativeFilePath);
        Path outputDirPath = Paths.get(config.getOutputDir(), conversationId, "current").toAbsolutePath().normalize();
        Path filePath = outputDirPath.resolve(normalizedPath).normalize();
        if (!filePath.startsWith(outputDirPath)) {
            throw new SecurityException("禁止访问输出目录之外的路径");
        }
        return filePath;
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

    private String getGenTypeWithCache(String conversationId) {
        return GEN_TYPE_CACHE.get(conversationId, id -> {
            Long appId = Long.parseLong(id);
            App app = appService.getById(appId);
            return app != null ? app.getCodeGenType() : null;
        });
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