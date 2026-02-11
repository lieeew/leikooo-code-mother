package com.leikooo.codemother.service.impl;

import com.leikooo.codemother.exception.BusinessException;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.vo.FileContentVO;
import com.leikooo.codemother.model.vo.FileListVO;
import com.leikooo.codemother.model.vo.FileTreeNodeVO;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.AppSourceService;
import com.leikooo.codemother.utils.ProjectPathUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
public class AppSourceServiceImpl implements AppSourceService {

    private static final Set<String> CODE_EXTENSIONS = Set.of(
            ".vue", ".js", ".ts", ".jsx", ".tsx", ".java", ".html", ".css",
            ".json", ".xml", ".yaml", ".yml", ".md", ".sql", ".py", ".go",
            ".rs", ".cpp", ".cc", ".c", ".h"
    );

    private static final Set<String> SKIP_DIRS = Set.of("node_modules", "dist");

    private static final int MAX_LINES = 2000;

    private final AppService appService;

    public AppSourceServiceImpl(AppService appService) {
        this.appService = appService;
    }

    @Override
    public FileTreeNodeVO getFileTree(Long appId) {
        Path basePath = validateAndResolvePath(appId, null);
        try {
            FileTreeNodeVO root = buildFileTree(basePath, basePath);
            return root;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "构建文件树失败");
        }
    }

    @Override
    public FileContentVO getFileContent(Long appId, String filePath, Integer start, Integer limit) {
        Path targetPath = validateAndResolvePath(appId, filePath);

        ThrowUtils.throwIf(!Files.isRegularFile(targetPath), ErrorCode.PARAMS_ERROR, "路径不是文件");

        try {
            List<String> allLines = Files.readAllLines(targetPath, StandardCharsets.UTF_8);
            int totalLines = allLines.size();

            int actualStart = start != null ? Math.max(0, start) : 0;
            int actualLimit = limit != null ? Math.min(limit, MAX_LINES) : 1000;
            int end = Math.min(actualStart + actualLimit, totalLines);

            StringBuilder content = new StringBuilder();
            for (int i = actualStart; i < end; i++) {
                content.append(allLines.get(i)).append("\n");
            }

            FileContentVO vo = new FileContentVO();
            vo.setFilePath(filePath);
            vo.setContent(content.toString());
            vo.setTotalLines(totalLines);
            vo.setReturnedLines(end - actualStart);
            vo.setLanguage(getLanguageType(getFileExtension(filePath)));
            vo.setEncoding("utf-8");
            vo.setHasMore(end < totalLines);

            return vo;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取文件失败");
        }
    }

    @Override
    public FileListVO getFileList(Long appId, String directory, Boolean recursive) {
        Path basePath = validateAndResolvePath(appId, directory);

        ThrowUtils.throwIf(!Files.isDirectory(basePath), ErrorCode.PARAMS_ERROR, "路径不是目录");

        List<FileListVO.FileInfo> fileInfos = new ArrayList<>();

        if (Boolean.TRUE.equals(recursive)) {
            try {
                Files.walkFileTree(basePath, new SimpleFileVisitor<>() {
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
                            fileInfos.add(createFileInfo(basePath, file, attrs));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "遍历文件失败");
            }
        } else {
            try (Stream<Path> paths = Files.list(basePath)) {
                paths.forEach(path -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        if (attrs.isDirectory()) {
                            String dirName = path.getFileName().toString();
                            if (!SKIP_DIRS.contains(dirName)) {
                                fileInfos.add(createFileInfo(basePath, path, attrs));
                            }
                        } else {
                            String ext = getFileExtension(path.getFileName().toString());
                            if (CODE_EXTENSIONS.contains(ext)) {
                                fileInfos.add(createFileInfo(basePath, path, attrs));
                            }
                        }
                    } catch (IOException e) {
                        log.warn("读取文件属性失败: {}", path);
                    }
                });
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "列出文件失败");
            }
        }

        FileListVO vo = new FileListVO();
        vo.setDirectory(directory != null ? directory : "");
        vo.setFiles(fileInfos);
        return vo;
    }

    private Path validateAndResolvePath(Long appId, String filePath) {
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        String projectPath = ProjectPathUtils.getProjectPath(appId.toString());
        Path basePath = Paths.get(projectPath).toAbsolutePath().normalize();

        ThrowUtils.throwIf(!Files.exists(basePath), ErrorCode.NOT_FOUND_ERROR, "应用代码目录不存在");

        if (StringUtils.isNotBlank(filePath)) {
            ThrowUtils.throwIf(filePath.contains("..") || Paths.get(filePath).isAbsolute(),
                ErrorCode.PARAMS_ERROR, "非法路径");

            String normalizedPath = normalizePath(filePath);
            Path targetPath = basePath.resolve(normalizedPath).normalize();

            ThrowUtils.throwIf(!targetPath.startsWith(basePath), ErrorCode.NO_AUTH_ERROR, "禁止访问目录外文件");

            return targetPath;
        }

        return basePath;
    }

    private String normalizePath(String path) {
        path = path.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    private FileTreeNodeVO buildFileTree(Path root, Path current) throws IOException {
        FileTreeNodeVO node = new FileTreeNodeVO();
        node.setId(UUID.randomUUID().toString());
        node.setName(current.getFileName().toString());

        if (Files.isDirectory(current)) {
            String dirName = current.getFileName().toString();
            if (SKIP_DIRS.contains(dirName)) {
                return null;
            }
            node.setType("directory");

            List<FileTreeNodeVO> children = new ArrayList<>();
            try (Stream<Path> paths = Files.list(current).sorted()) {
                paths.forEach(path -> {
                    try {
                        FileTreeNodeVO child = buildFileTree(root, path);
                        if (child != null) {
                            children.add(child);
                        }
                    } catch (IOException e) {
                        log.warn("构建文件树失败: {}", path);
                    }
                });
            }
            node.setChildren(children);
        } else {
            String ext = getFileExtension(node.getName());
            if (!CODE_EXTENSIONS.contains(ext)) {
                return null;
            }
            node.setType("file");
            node.setPath(root.relativize(current).toString().replace('\\', '/'));
            node.setSize(Files.size(current));
            node.setExtension(ext);
        }
        return node;
    }

    private FileListVO.FileInfo createFileInfo(Path root, Path file, BasicFileAttributes attrs) {
        FileListVO.FileInfo info = new FileListVO.FileInfo();
        info.setName(file.getFileName().toString());
        info.setPath(root.relativize(file).toString().replace('\\', '/'));
        info.setType(attrs.isDirectory() ? "directory" : "file");
        info.setSize(attrs.size());
        info.setModifyTime(attrs.lastModifiedTime().toInstant().toString());
        return info;
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot).toLowerCase();
        }
        return "";
    }

    private String getLanguageType(String extension) {
        return switch (extension) {
            case ".vue" -> "vue";
            case ".js" -> "javascript";
            case ".ts" -> "typescript";
            case ".jsx" -> "jsx";
            case ".tsx" -> "tsx";
            case ".java" -> "java";
            case ".html" -> "html";
            case ".css" -> "css";
            case ".json" -> "json";
            case ".xml" -> "xml";
            case ".yaml", ".yml" -> "yaml";
            case ".md" -> "markdown";
            case ".sql" -> "sql";
            case ".py" -> "python";
            case ".go" -> "go";
            case ".rs" -> "rust";
            case ".cpp", ".cc" -> "cpp";
            case ".c" -> "c";
            case ".h" -> "c";
            default -> "plaintext";
        };
    }
}
