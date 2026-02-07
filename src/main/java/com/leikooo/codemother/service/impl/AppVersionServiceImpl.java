package com.leikooo.codemother.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leikooo.codemother.config.CosClientConfig;
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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author leikooo
 * @description 针对表【app_version(应用版本记录)】的数据库操作Service实现
 * @createDate 2026-02-04 23:52:49
 */
@Slf4j
@Service
public class AppVersionServiceImpl extends ServiceImpl<AppVersionMapper, AppVersion>
        implements AppVersionService {

    private final CosManager cosManager;
    private final AppService appService;
    private final CosClientConfig cosClientConfig;
    private final VersionCache versionCache;
    private final ObjectMapper objectMapper;

    public AppVersionServiceImpl(CosManager cosManager,
                                 AppService appService,
                                 CosClientConfig cosClientConfig,
                                 VersionCache versionCache,
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
            // 1. 创建 v0 目录和 current 目录
            Path v0Path = Paths.get("generated-apps", appId.toString(), "v0", "src");
            Path currentPath = Paths.get("generated-apps", appId.toString(), "current", "src");
            Files.createDirectories(v0Path);
            Files.createDirectories(currentPath);

            // 2. 保存 AppVersion 记录到数据库
            AppVersion version = AppVersion.builder()
                    .appId(appId)
                    .userId(getLoginUserId(appId))
                    .versionNum(0)
                    .fileCount(0)
                    .fileSize(0L)
                    .build();
            this.save(version);

            // 3. 初始化缓存
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

        // 1. 获取当前最大版本号
        Integer maxVersion = versionCache.get(appIdStr);
        if (maxVersion == null) {
            maxVersion = getMaxVersionNum(appId);
        }
        int newVersionNum = maxVersion + 1;

        try {
            // 2. 创建版本目录
            Path versionPath = Paths.get("generated-apps", appIdStr, "v" + newVersionNum);
            Files.createDirectories(versionPath);

            // 3. 复制 current 目录下的文件到版本目录
            Path currentPath = Paths.get("generated-apps", appIdStr, "current");
            if (Files.exists(currentPath)) {
                copyDirectory(currentPath, versionPath);
            }

            // 4. 生成 metadata.json
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("versionNum", newVersionNum);
            metadata.put("appId", appIdStr);
            metadata.put("status", VersionStatusEnum.SOURCE_BUILDING.name());
            metadata.put("createTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            saveMetadata(versionPath, metadata);

            // 5. 保存版本记录到数据库
            int fileCount = countFiles(versionPath);
            AppVersion version = AppVersion.builder()
                    .appId(appId)
                    .userId(getLoginUserId(appId))
                    .versionNum(newVersionNum)
                    .fileCount(fileCount)
                    .fileSize(0L)
                    .build();
            this.save(version);

            // 6. 更新缓存
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
            // 1. 获取当前版本号
            Integer versionNum = versionCache.get(appIdStr);
            if (versionNum == null) {
                versionNum = getMaxVersionNum(appId);
            }

            // 2. 执行构建
            String projectPath = ProjectPathUtils.getProjectPath(appIdStr);
            VueBuildUtils.BuildResult buildResult = VueBuildUtils.buildVueProject(projectPath);
            BuildResultEnum buildResultEnum = BuildResultEnum.fromExitCode(buildResult.exitCode());

            // 3. 更新 metadata.json
            Path versionPath = Paths.get("generated-apps", appIdStr, "v" + versionNum);
            Path metadataPath = versionPath.resolve("metadata.json");
            if (Files.exists(metadataPath)) {
                Map<String, Object> metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
                if (buildResultEnum == BuildResultEnum.SUCCESS) {
                    metadata.put("status", VersionStatusEnum.SUCCESS.name());
                    metadata.put("buildTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                } else {
                    metadata.put("status", VersionStatusEnum.NEED_FIX.name());
                    metadata.put("errorLog", buildResult.errorLog());
                }
                saveMetadata(versionPath, metadata);
            }

            // 4. 更新数据库状态
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

    private void copyDirectory(Path source, Path target) throws IOException {
        Path distToSkip = source.resolve("dist");
        Path nodeModulesToSkip = source.resolve("node_modules");

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // 跳过 dist 和 node_modules
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




