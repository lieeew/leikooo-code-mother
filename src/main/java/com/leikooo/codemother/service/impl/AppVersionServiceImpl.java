package com.leikooo.codemother.service.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leikooo.codemother.config.CosClientConfig;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.manager.CosManager;
import com.leikooo.codemother.mapper.AppVersionMapper;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.entity.AppVersion;
import com.leikooo.codemother.model.enums.BuildResultEnum;
import com.leikooo.codemother.model.enums.VersionStatusEnum;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.AppVersionService;
import com.leikooo.codemother.utils.ProjectPathUtils;
import com.leikooo.codemother.utils.VersionCache;
import com.leikooo.codemother.utils.VueBuildUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class AppVersionServiceImpl extends ServiceImpl<AppVersionMapper, AppVersion>
        implements AppVersionService {

    private final CosManager cosManager;
    private final AppService appService;
    private final CosClientConfig cosClientConfig;
    private final VersionCache versionCache;
    private final ObjectMapper objectMapper;

    public AppVersionServiceImpl(CosManager cosManager, AppService appService,
                                CosClientConfig cosClientConfig, VersionCache versionCache,
                                ObjectMapper objectMapper) {
        this.cosManager = cosManager;
        this.appService = appService;
        this.cosClientConfig = cosClientConfig;
        this.versionCache = versionCache;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AppVersion> listByAppId(Long appId) {
        return this.lambdaQuery()
                .eq(AppVersion::getAppId, appId)
                .eq(AppVersion::getIsDelete, 0)
                .orderByDesc(AppVersion::getVersionNum)
                .list();
    }

    @Override
    public int getMaxVersionNum(Long appId) {
        AppVersion maxVersion = this.lambdaQuery()
                .eq(AppVersion::getAppId, appId)
                .eq(AppVersion::getIsDelete, 0)
                .orderByDesc(AppVersion::getVersionNum)
                .one();
        return maxVersion != null ? maxVersion.getVersionNum() : 0;
    }

    @Override
    public AppVersion getByVersionNum(Long appId, Integer versionNum) {
        return this.lambdaQuery()
                .eq(AppVersion::getAppId, appId)
                .eq(AppVersion::getVersionNum, versionNum)
                .eq(AppVersion::getIsDelete, 0)
                .one();
    }

    @Override
    public void initVersion(Long appId) {
        try {
            Path v0Path = Paths.get("generated-apps", appId.toString(), "v0", "src");
            Path currentPath = Paths.get("generated-apps", appId.toString(), "current", "src");
            Files.createDirectories(v0Path);
            Files.createDirectories(currentPath);

            AppVersion version = AppVersion.builder()
                    .appId(appId)
                    .userId(getLoginUserId(appId))
                    .versionNum(0)
                    .fileCount(0)
                    .fileSize(0L)
                    .build();
            this.save(version);

            versionCache.set(appId.toString(), 0);
            log.info("[AppVersion] 初始化 v0 和 current 完成: appId={}", appId);
        } catch (IOException e) {
            log.error("[AppVersion] 初始化失败: appId={}", appId, e);
            throw new RuntimeException("初始化失败", e);
        }
    }

    private byte[] getLoginUserId(Long appId) {
        App app = appService.lambdaQuery()
                .eq(App::getId, appId)
                .select(App::getId)
                .select(App::getUserId)
                .one();
        return app.getUserId();
    }

    @Override
    public Integer saveVersion(String appIdStr) {
        Long appId = Long.parseLong(appIdStr);
        Integer maxVersion = versionCache.get(appIdStr);
        if (maxVersion == null) {
            maxVersion = getMaxVersionNum(appId);
        }
        int newVersionNum = maxVersion + 1;

        try {
            Path versionPath = Paths.get("generated-apps", appIdStr, "v" + newVersionNum);
            Files.createDirectories(versionPath);

            Path currentPath = Paths.get("generated-apps", appIdStr, "current");
            if (Files.exists(currentPath)) {
                copyDirectory(currentPath, versionPath);
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("versionNum", newVersionNum);
            metadata.put("appId", appIdStr);
            metadata.put("status", VersionStatusEnum.SOURCE_BUILDING.name());
            metadata.put("createTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            saveMetadata(versionPath, metadata);

            int fileCount = countFiles(versionPath);
            AppVersion version = AppVersion.builder()
                    .appId(appId)
                    .userId(getLoginUserId(appId))
                    .versionNum(newVersionNum)
                    .fileCount(fileCount)
                    .fileSize(0L)
                    .build();
            this.save(version);

            appService.lambdaUpdate()
                    .eq(App::getId, appId)
                    .set(App::getCurrentVersionNum, newVersionNum)
                    .update();

            versionCache.set(appIdStr, newVersionNum);
            log.info("[AppVersion] 保存版本完成: appId={}, version=v{}", appId, newVersionNum);
            return newVersionNum;

        } catch (IOException e) {
            log.error("[AppVersion] 保存版本失败: appId={}", appId, e);
            throw new RuntimeException("保存版本失败", e);
        }
    }

    @Override
    public void updateBuildStatus(String appIdStr) {
        Long appId = Long.parseLong(appIdStr);

        try {
            Integer versionNum = versionCache.get(appIdStr);
            if (versionNum == null) {
                versionNum = getMaxVersionNum(appId);
            }

            String projectPath = ProjectPathUtils.getProjectPath(appIdStr);
            VueBuildUtils.BuildResult buildResult = VueBuildUtils.buildVueProject(projectPath, appIdStr);
            BuildResultEnum buildResultEnum = BuildResultEnum.fromExitCode(buildResult.exitCode());

            Path versionPath = Paths.get("generated-apps", appIdStr, "v" + versionNum);
            Path metadataPath = versionPath.resolve("metadata.json");
            if (Files.exists(metadataPath)) {
                Map<String, Object> metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
                if (buildResultEnum == BuildResultEnum.SUCCESS) {
                    metadata.put("status", VersionStatusEnum.SUCCESS.name());
                    metadata.put("buildTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                } else {
                    metadata.put("status", VersionStatusEnum.NEED_FIX.name());
                    metadata.put("errorLog", buildResult.fullLog());
                }
                saveMetadata(versionPath, metadata);
            }

            String status = buildResultEnum == BuildResultEnum.SUCCESS
                    ? VersionStatusEnum.SUCCESS.name()
                    : VersionStatusEnum.NEED_FIX.name();
            this.lambdaUpdate()
                    .eq(AppVersion::getAppId, appId)
                    .eq(AppVersion::getVersionNum, versionNum)
                    .set(AppVersion::getStatus, status)
                    .update();

            log.info("[AppVersion] 更新构建状态: appId={}, version=v{}, status={}", appId, versionNum, status);

        } catch (Exception e) {
            log.error("[AppVersion] 更新构建状态失败: appId={}", appId, e);
        }
    }

    @Override
    public void rollback(Long appId, Integer versionNum) {
        Path currentPath = Paths.get("generated-apps", appId.toString(), "current");
        Path versionPath = Paths.get("generated-apps", appId.toString(), "v" + versionNum);

        try {
            if (Files.exists(currentPath)) {
                clearDirectory(currentPath);
            }

            if (Files.exists(versionPath)) {
                copyDirectoryExclude(versionPath, currentPath, "metadata.json");
                log.info("[Rollback] 本地回滚成功: appId={}, version=v{}", appId, versionNum);
            } else {
                AppVersion version = getByVersionNum(appId, versionNum);
                ThrowUtils.throwIf(version == null, ErrorCode.NOT_FOUND_ERROR, "版本不存在");
                downloadAndExtract(version.getFileUrl(), currentPath.toString());
                log.info("[Rollback] COS 回滚成功: appId={}, version=v{}", appId, versionNum);
            }
            VueBuildUtils.buildVueProject(currentPath.toString());
        } catch (IOException e) {
            log.error("[Rollback] 回滚失败: appId={}, version=v{}", appId, versionNum, e);
            throw new RuntimeException("回滚失败", e);
        }
    }

    private void clearDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        log.warn("[Clear] 文件被占用，跳过: {}", file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        Files.delete(dir);
                    } catch (IOException e) {
                        log.warn("[Clear] 目录被占用，跳过: {}", dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("[Clear] 清空目录失败: {}", dir, e);
        }
    }

    private void copyDirectoryExclude(Path source, Path target, String... excludeFiles) throws IOException {
        Set<String> excludeSet = Set.of(excludeFiles);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (excludeSet.contains(file.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void downloadAndExtract(String cosKey, String targetPath) throws IOException {
        File targetDir = new File(targetPath);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        File tempZip = File.createTempFile("download", ".zip");
        try (FileOutputStream fos = new FileOutputStream(tempZip)) {
            IOUtils.copy(cosManager.getObject(cosKey).getObjectContent(), fos);
        }

        try (FileInputStream fis = new FileInputStream(tempZip);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
        tempZip.delete();
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Path distToSkip = source.resolve("dist");
        Path nodeModulesToSkip = source.resolve("node_modules");
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(distToSkip) || dir.equals(nodeModulesToSkip)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private int countFiles(Path path) throws IOException {
        return (int) Files.walk(path)
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains("node_modules"))
                .filter(p -> !p.toString().contains("dist"))
                .count();
    }

    private void saveMetadata(Path versionPath, Map<String, Object> metadata) throws IOException {
        Path metadataPath = versionPath.resolve("metadata.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataPath.toFile(), metadata);
    }
}
