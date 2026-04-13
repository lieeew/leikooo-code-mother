package com.leikooo.codemother.controller;

import com.leikooo.codemother.commen.BaseResponse;
import com.leikooo.codemother.commen.ResultUtils;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.model.entity.AppVersion;
import com.leikooo.codemother.model.vo.AppVersionVO;
import com.leikooo.codemother.service.AppVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author leikooo
 */
@Slf4j
@RestController
@RequestMapping("/app/version")
public class AppVersionController {

    private final AppVersionService appVersionService;

    public AppVersionController(AppVersionService appVersionService) {
        this.appVersionService = appVersionService;
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

    @GetMapping("/download/{appId}/{versionNum}")
    public ResponseEntity<byte[]> downloadVersion(
            @PathVariable(name = "appId") Long appId,
            @PathVariable(name = "versionNum") Integer versionNum) {
        byte[] zipContent = appVersionService.downloadVersion(appId, versionNum);

        String fileName = "app-" + appId + "-v" + versionNum + ".zip";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                .body(zipContent);
    }
}
