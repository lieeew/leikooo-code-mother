package com.leikooo.codemother.service.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leikooo.codemother.config.CosClientConfig;
import com.leikooo.codemother.constant.ResourcePathConstant;
import com.leikooo.codemother.event.AppCodeRegeneratedEvent;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.manager.CosManager;
import com.leikooo.codemother.mapper.AppVersionMapper;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.entity.AppVersion;
import com.leikooo.codemother.model.enums.BuildResultEnum;
import com.leikooo.codemother.model.enums.VersionStatusEnum;
import com.leikooo.codemother.model.vo.RuntimeCheckResultVO;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.AppVersionService;
import com.leikooo.codemother.utils.ProjectPathUtils;
import com.leikooo.codemother.utils.RuntimeCheckUtils;
import com.leikooo.codemother.utils.VersionCache;
import com.leikooo.codemother.utils.VueBuildUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
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
import java.util.stream.Stream;
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
    private final ApplicationEventPublisher eventPublisher;

    public AppVersionServiceImpl(CosManager cosManager, AppService appService,
                                CosClientConfig cosClientConfig, VersionCache versionCache,
                                ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.cosManager = cosManager;
        this.appService = appService;
        this.cosClientConfig = cosClientConfig;
        this.versionCache = versionCache;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
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

    private long calculateDirectorySize(Path path) {
        long size = 0;
        try (Stream<Path> walk = Files.walk(path)) {
            size = walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            log.warn("[AppVersion] 计算目录大小失败: path={}, error={}", path, e.getMessage());
        }
        return size;
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
            Path versionPath = Paths.get(ResourcePathConstant.GENERATED_APPS_DIR, appIdStr, "v" + newVersionNum);
            Files.createDirectories(versionPath);

            Path currentPath = Paths.get(ResourcePathConstant.GENERATED_APPS_DIR, appIdStr, "current");
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
            long fileSize = calculateDirectorySize(versionPath);
            AppVersion version = AppVersion.builder()
                    .appId(appId)
                    .userId(getLoginUserId(appId))
                    .versionNum(newVersionNum)
                    .fileCount(fileCount)
                    .fileSize(fileSize)
                    .build();
            this.save(version);

            appService.lambdaUpdate()
                    .eq(App::getId, appId)
                    .set(App::getCurrentVersionNum, newVersionNum)
                    .update();

            versionCache.set(appIdStr, newVersionNum);
            log.info("[AppVersion] 保存版本完成: appId={}, version=v{}", appId, newVersionNum);

            eventPublisher.publishEvent(new AppCodeRegeneratedEvent(this, appId));

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

            Path versionPath = Paths.get(ResourcePathConstant.GENERATED_APPS_DIR, appIdStr, "v" + versionNum);
            Path metadataPath = versionPath.resolve("metadata.json");
            if (Files.exists(metadataPath)) {
                Map<String, Object> metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
                if (buildResultEnum == BuildResultEnum.SUCCESS) {
                    metadata.put("status", VersionStatusEnum.SUCCESS.name());
                    metadata.put("buildTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    // Build 成功后触发运行时检测
                    asyncRuntimeCheck(appIdStr);
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

    @Async("rollbackExecutor")
    @Override
    public void rollback(Long appId, Integer versionNum) {
        Path currentPath = Paths.get(ResourcePathConstant.GENERATED_APPS_DIR, appId.toString(), "current");
        Path versionPath = Paths.get(ResourcePathConstant.GENERATED_APPS_DIR, appId.toString(), "v" + versionNum);

        try {
            if (Files.exists(currentPath)) {
                clearDirectory(currentPath);
            }

            if (Files.exists(versionPath)) {
                copyDirectoryExclude(versionPath, currentPath, "metadata.json");
                appService.lambdaUpdate()
                        .eq(App::getId, appId)
                        .set(App::getCurrentVersionNum, versionNum)
                        .update();
                log.info("[Rollback] 本地回滚成功: appId={}, version=v{}", appId, versionNum);
            } else {
                AppVersion version = getByVersionNum(appId, versionNum);
                ThrowUtils.throwIf(version == null, ErrorCode.NOT_FOUND_ERROR, "版本不存在");
                downloadAndExtract(version.getFileUrl(), currentPath.toString());
                appService.lambdaUpdate()
                        .eq(App::getId, appId)
                        .set(App::getCurrentVersionNum, versionNum)
                        .update();
                log.info("[Rollback] COS 回滚成功: appId={}, version=v{}", appId, versionNum);
            }
            VueBuildUtils.buildVueProject(currentPath.toString());
        } catch (IOException e) {
            log.error("[Rollback] 回滚失败: appId={}, version=v{}", appId, versionNum, e);
            throw new RuntimeException("回滚失败", e);
        }
    }

    @Async("runtimeCheckExecutor")
    @Override
    public void asyncRuntimeCheck(String appIdStr) {
        Long appId = Long.parseLong(appIdStr);
        log.info("[RuntimeCheck] 开始运行时检测: appId={}", appId);

        try {
            Integer versionNum = versionCache.get(appIdStr);
            if (versionNum == null) {
                versionNum = getMaxVersionNum(appId);
            }

            Path versionPath = buildVersionPath(appId, versionNum);
            Map<String, Object> metadata = readMetadata(versionPath);

            String status = (String) metadata.get("status");
//            if (!VersionStatusEnum.NEED_FIX.name().equals(status)) {
//                log.info("[RuntimeCheck] Build 未成功，跳过运行时检测: appId={}, status={}", appId, status);
//                return;
//            }

            String projectPath = ProjectPathUtils.getProjectPath(appIdStr);
            RuntimeCheckUtils.RuntimeCheckResult result = RuntimeCheckUtils.checkRuntime(projectPath, appIdStr);

            metadata.put("runtimeErrors", result.consoleErrors());
            metadata.put("runtimeExceptions", result.jsExceptions());
            metadata.put("runtimeCheckTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            metadata.put("hasScreenshot", result.screenshotPath() != null);

            if (result.hasErrors()) {
                metadata.put("runtimeErrorLog", result.fullLog());
                metadata.put("status", VersionStatusEnum.NEED_FIX.name());
                this.lambdaUpdate()
                        .eq(AppVersion::getAppId, appId)
                        .eq(AppVersion::getVersionNum, versionNum)
                        .set(AppVersion::getStatus, VersionStatusEnum.NEED_FIX.name())
                        .update();
            }
            saveMetadata(versionPath, metadata);

            log.info("[RuntimeCheck] 运行时检测完成: appId={}, hasErrors={}, consoleErrors={}, jsExceptions={}",
                    appId, result.hasErrors(), result.consoleErrors().size(), result.jsExceptions().size());

        } catch (Exception e) {
            log.error("[RuntimeCheck] 运行时检测失败: appId={}", appId, e);
        }
    }

    private App getValidatedApp(Long appId) {
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        return app;
    }

    private Path buildVersionPath(Long appId, Integer versionNum) {
        return Paths.get(ResourcePathConstant.GENERATED_APPS_DIR, appId.toString(), "v" + versionNum);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(Path versionPath) {
        Path metadataPath = versionPath.resolve("metadata.json");
        ThrowUtils.throwIf(!Files.exists(metadataPath), ErrorCode.SYSTEM_ERROR, "metadata.json 不存在");
        try {
            return objectMapper.readValue(metadataPath.toFile(), Map.class);
        } catch (IOException e) {
            throw new RuntimeException("读取 metadata.json 失败", e);
        }
    }

    @Override
    public String getFixErrorMessage(Long appId) {
        App app = getValidatedApp(appId);
        Integer currentVersion = app.getCurrentVersionNum();

        AppVersion version = getByVersionNum(appId, currentVersion);
        ThrowUtils.throwIf(version == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!VersionStatusEnum.NEED_FIX.name().equals(version.getStatus()),
                ErrorCode.OPERATION_ERROR, "当前版本无需修复");

        Map<String, Object> metadata = readMetadata(buildVersionPath(appId, currentVersion));
        String errorLog = (String) metadata.getOrDefault("errorLog", "");
        String runtimeErrorLog = (String) metadata.getOrDefault("runtimeErrorLog", "");

        StringBuilder errorMsg = new StringBuilder();
        if (!errorLog.isEmpty()) {
            errorMsg.append("构建错误:\n").append(errorLog);
        }
        if (!runtimeErrorLog.isEmpty()) {
            if (!errorMsg.isEmpty()) errorMsg.append("\n\n");
            errorMsg.append("运行时错误:\n").append(runtimeErrorLog);
        }

        asyncRuntimeCheck(appId.toString());
        return String.format("遇到了下面的 BUG: %s", errorMsg);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RuntimeCheckResultVO getRuntimeCheckResult(Long appId) {
        App app = getValidatedApp(appId);
        Map<String, Object> metadata = readMetadata(buildVersionPath(appId, app.getCurrentVersionNum()));

        List<String> consoleErrors = (List<String>) metadata.getOrDefault("runtimeErrors", List.of());
        List<String> jsExceptions = (List<String>) metadata.getOrDefault("runtimeExceptions", List.of());
        boolean hasScreenshot = Boolean.TRUE.equals(metadata.getOrDefault("hasScreenshot", false));
        String checkTime = (String) metadata.getOrDefault("runtimeCheckTime", "");
        boolean hasErrors = !consoleErrors.isEmpty() || !jsExceptions.isEmpty();

        return new RuntimeCheckResultVO(hasErrors, consoleErrors, jsExceptions, hasScreenshot, checkTime);
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
