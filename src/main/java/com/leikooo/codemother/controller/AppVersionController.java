package com.leikooo.codemother.controller;

import com.leikooo.codemother.commen.BaseResponse;
import com.leikooo.codemother.commen.ResultUtils;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.manager.CosManager;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.entity.AppVersion;
import com.leikooo.codemother.model.vo.AppVersionVO;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.AppVersionService;
import com.leikooo.codemother.service.UserService;
import com.leikooo.codemother.utils.VueBuildUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author leikooo
 */
@Slf4j
@RestController
@RequestMapping("/app/version")
public class AppVersionController {

    private final AppVersionService appVersionService;
    private final AppService appService;
    private final UserService userService;
    private final CosManager cosManager;

    public AppVersionController(AppVersionService appVersionService,
                                AppService appService,
                                UserService userService,
                                CosManager cosManager) {
        this.appVersionService = appVersionService;
        this.appService = appService;
        this.userService = userService;
        this.cosManager = cosManager;
    }

    /**
     * 获取应用版本列表
     * @param appId 应用ID
     * @return 版本列表
     */
    @GetMapping("/list/{appId}")
    public BaseResponse<List<AppVersionVO>> listVersions(@PathVariable(name = "appId") Long appId) {
        ThrowUtils.throwIf(appId == null, ErrorCode.PARAMS_ERROR);
        List<AppVersion> appVersions = appVersionService.listByAppId(appId);
        return ResultUtils.success(appVersions.stream().map(AppVersionVO::toVO).toList());
    }

    /**
     * 回滚到指定版本
     * @param appId 应用ID
     * @param versionNum 版本号
     * @return 是否成功
     */
    @PostMapping("/rollback")
    public BaseResponse<Boolean> rollback(
            @RequestParam(name = "appId") Long appId,
            @RequestParam(name = "versionNum") Integer versionNum) {
        ThrowUtils.throwIf(appId == null || versionNum == null, ErrorCode.PARAMS_ERROR);

        // 1. 获取版本信息
        AppVersion version = appVersionService.getByVersionNum(appId, versionNum);
        ThrowUtils.throwIf(version == null, ErrorCode.NOT_FOUND_ERROR, "版本不存在");

        // 2. 获取应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        try {
            // 3. 从 COS 下载版本
            String localPath = "generated-apps/" + appId;
            downloadAndExtract(version.getFileUrl(), localPath);

            // 4. 重新构建
            VueBuildUtils.BuildResult buildResult = VueBuildUtils.buildVueProject(localPath);
            ThrowUtils.throwIf(!buildResult.success(), ErrorCode.SYSTEM_ERROR, "构建失败");

            // 5. 保存新版本
            appVersionService.saveVersion(app.getId().toString());

            log.info("[Rollback] 回滚成功: appId={}, version={}", appId, versionNum);
            return ResultUtils.success(true);

        } catch (Exception e) {
            log.error("[Rollback] 回滚失败: appId={}, version={}", appId, versionNum, e);
            throw new RuntimeException("回滚失败", e);
        }
    }

    /**
     * 下载版本代码
     * @param appId 应用ID
     * @param versionNum 版本号
     * @return zip 文件
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadVersion(
            @RequestParam(name = "appId") Long appId,
            @RequestParam(name = "versionNum") Integer versionNum) {
        AppVersion version = appVersionService.getByVersionNum(appId, versionNum);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            // 从 COS 下载
            File tempFile = File.createTempFile("version-" + versionNum, ".zip");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                IOUtils.copy(cosManager.getObject(version.getFileUrl()).getObjectContent(), fos);
            }

            byte[] fileContent = Files.readAllBytes(tempFile.toPath());
            tempFile.delete();

            String fileName = "app-" + appId + "-v" + versionNum + ".zip";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                    .body(fileContent);

        } catch (Exception e) {
            log.error("[Download] 下载失败: appId={}, version={}", appId, versionNum, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void downloadAndExtract(String cosKey, String targetPath) throws IOException {
        // 创建目标目录
        File targetDir = new File(targetPath);
        if (targetDir.exists()) {
            deleteDirectory(targetDir);
        }
        targetDir.mkdirs();

        // 下载并解压
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

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}
