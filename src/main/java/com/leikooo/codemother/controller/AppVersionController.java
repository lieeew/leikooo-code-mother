package com.leikooo.codemother.controller;

import com.leikooo.codemother.commen.BaseResponse;
import com.leikooo.codemother.commen.ResultUtils;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.manager.CosManager;
import com.leikooo.codemother.model.entity.AppVersion;
import com.leikooo.codemother.model.vo.AppVersionVO;
import com.leikooo.codemother.service.AppVersionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
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
    private final CosManager cosManager;

    public AppVersionController(AppVersionService appVersionService, CosManager cosManager) {
        this.appVersionService = appVersionService;
        this.cosManager = cosManager;
    }

    @GetMapping("/list/{appId}")
    public BaseResponse<List<AppVersionVO>> listVersions(@PathVariable(name = "appId") Long appId) {
        ThrowUtils.throwIf(appId == null, ErrorCode.PARAMS_ERROR);
        List<AppVersion> appVersions = appVersionService.listByAppId(appId);
        return ResultUtils.success(appVersions.stream().map(AppVersionVO::toVO).toList());
    }

    @PostMapping("/rollback")
    public BaseResponse<Boolean> rollback(
            @RequestParam(name = "appId") Long appId,
            @RequestParam(name = "versionNum") Integer versionNum) {
        ThrowUtils.throwIf(appId == null || versionNum == null, ErrorCode.PARAMS_ERROR);
        appVersionService.rollback(appId, versionNum);
        return ResultUtils.success(true);
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadVersion(
            @RequestParam(name = "appId") Long appId,
            @RequestParam(name = "versionNum") Integer versionNum) {
        AppVersion version = appVersionService.getByVersionNum(appId, versionNum);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            File tempFile = File.createTempFile("version-" + versionNum, ".zip");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                IOUtils.copy(cosManager.getObject(version.getFileUrl()).getObjectContent(), fos);
            }

            byte[] fileContent = Files.readAllBytes(tempFile.toPath());
            tempFile.delete();

            String fileName = "app-" + appId + "-v" + versionNum + ".zip";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                    .body(fileContent);

        } catch (Exception e) {
            log.error("[Download] 下载失败: appId={}, version={}", appId, versionNum, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
