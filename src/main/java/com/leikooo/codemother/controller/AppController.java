package com.leikooo.codemother.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leikooo.codemother.ai.GenerationManager;
import com.leikooo.codemother.annotation.AuthCheck;
import com.leikooo.codemother.commen.BaseResponse;
import com.leikooo.codemother.commen.DeleteRequest;
import com.leikooo.codemother.commen.ResultUtils;
import com.leikooo.codemother.constant.AppConstant;
import com.leikooo.codemother.constant.UserConstant;
import com.leikooo.codemother.exception.BusinessException;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.model.dto.AppQueryDto;
import com.leikooo.codemother.model.dto.CreatAppDto;
import com.leikooo.codemother.model.dto.GenAppDto;
import com.leikooo.codemother.model.dto.request.app.AppAdminUpdateRequest;
import com.leikooo.codemother.model.dto.request.app.AppQueryRequest;
import com.leikooo.codemother.model.dto.request.app.AppUpdateRequest;
import com.leikooo.codemother.model.dto.request.app.CreatAppRequest;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.entity.AppVersion;
import com.leikooo.codemother.model.enums.VersionStatusEnum;
import com.leikooo.codemother.model.vo.AppVO;
import com.leikooo.codemother.model.vo.FileContentVO;
import com.leikooo.codemother.model.vo.FileListVO;
import com.leikooo.codemother.model.vo.FileTreeNodeVO;
import com.leikooo.codemother.model.vo.UserVO;
import com.leikooo.codemother.service.AppService;
import com.leikooo.codemother.service.AppSourceService;
import com.leikooo.codemother.service.AppVersionService;
import com.leikooo.codemother.service.UserService;
import com.leikooo.codemother.utils.UuidV7Generator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/1/2
 * @description
 */
@RestController
@RequestMapping("/app")
public class AppController {
    private final AppService appService;
    private final UserService userService;
    private final GenerationManager generationManager;
    private final AppVersionService appVersionService;
    private final AppSourceService appSourceService;

    public AppController(AppService appService, UserService userService, GenerationManager generationManager, AppVersionService appVersionService, AppSourceService appSourceService) {
        this.appService = appService;
        this.userService = userService;
        this.generationManager = generationManager;
        this.appVersionService = appVersionService;
        this.appSourceService = appSourceService;
    }

    @PostMapping("/add")
    public BaseResponse<Long> createApp(@Valid @RequestBody CreatAppRequest creatAppRequest) {
        ThrowUtils.throwIf(Objects.isNull(creatAppRequest), ErrorCode.PARAMS_ERROR);
        UserVO userLogin = userService.getUserLogin();
        return ResultUtils.success(appService.createApp(new CreatAppDto(creatAppRequest, userLogin)));
    }

    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> generateApp(
            @RequestParam(name = "appId") Long appId,
            @RequestParam(name = "message") String message
    ) {
        ThrowUtils.throwIf(StringUtils.isEmpty(message) || Objects.isNull(appId), ErrorCode.PARAMS_ERROR);
        GenAppDto genAppDto = new GenAppDto(message, appId.toString(), userService.getUserLogin());
        Flux<String> stringFlux = appService.genAppCode(genAppDto);
        return stringFlux
                .map(chunk -> {
                    Map<String, String> wrapper = Map.of("d", chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(
                        // 发送结束事件
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                )).doFinally(signalType -> {
                    // doFinally
                });
    }

    @PostMapping("/cancel/gen")
    public BaseResponse<Boolean> cancelGeneration(@RequestParam(name = "appId") Long appId) {
        ThrowUtils.throwIf(Objects.isNull(appId), ErrorCode.PARAMS_ERROR);
        boolean cancelled = generationManager.cancel(appId.toString());
        return ResultUtils.success(cancelled);
    }

    /**
     * 更新应用（用户只能更新自己的应用名称）
     *
     * @param appUpdateRequest 更新请求
     * @param request          请求
     * @return 更新结果
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO loginUser = userService.getUserLogin();
        long id = appUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人可更新
        if (!Arrays.equals(oldApp.getUserId(), UuidV7Generator.stringToBytes(loginUser.getId()))) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        App app = new App();
        app.setId(id);
        app.setAppName(appUpdateRequest.getAppName());
        // 设置编辑时间
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVO(@RequestParam(name = "id") Long id) {
        ThrowUtils.throwIf(Objects.isNull(id), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(appService.getAppVO(id));
    }

    @AuthCheck(mustRole = UserConstant.USER_ROLE)
    @PostMapping("/my/list/page/vo")
    public BaseResponse<List<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        int pageSize = appQueryRequest.getPageSize();
        int current = appQueryRequest.getCurrent();
        ThrowUtils.throwIf(pageSize > 50, ErrorCode.PARAMS_ERROR);
        UserVO userLogin = userService.getUserLogin();
        Page<App> page = appService.page(
                new Page<>(current, pageSize),
                appService.getQueryWrapper(AppQueryDto.toDto(appQueryRequest, userLogin))
        );
        return ResultUtils.success(appService.getAppVOList(page.getRecords()));
    }

    /**
     * 分页获取精选应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 精选应用列表
     */
    @PostMapping("/good/list/page/vo")
    @Cacheable(
            value = "good_app_page",
            key = "T(com.leikooo.codemother.utils.CacheKeyGenerator).generateKey(#a0)",
            condition = "#a0.pageSize <= 10"
    )
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getCurrent();
        // 只查询精选的应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        QueryWrapper<App> queryWrapper = appService.getQueryWrapper(AppQueryDto.toDto(appQueryRequest));
        // 分页查询
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVoPage = new Page<>(pageNum, pageSize, appPage.getTotal());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVoPage.setRecords(appVOList);
        return ResultUtils.success(appVoPage);
    }

    /**
     * 管理员分页获取应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 应用列表
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = appQueryRequest.getCurrent();
        int pageSize = appQueryRequest.getPageSize();
        QueryWrapper<App> queryWrapper = appService.getQueryWrapper(AppQueryDto.toDto(appQueryRequest));
        // 分页查询
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVoPage = new Page<>(pageNum, pageSize, appPage.getTotal());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVoPage.setRecords(appVOList);
        return ResultUtils.success(appVoPage);
    }

    /**
     * 管理员删除应用
     *
     * @param deleteRequest 删除请求
     * @return 删除结果
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getAppId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getAppId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 管理员更新应用
     *
     * @param appAdminUpdateRequest 更新请求
     * @return 更新结果
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        if (appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = appAdminUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, app);
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 获取封装类
        return ResultUtils.success(appService.getAppVO(id));
    }

    /**
     * 获取构建错误信息（供前端用户确认后发送修复）
     *
     * @param appId 应用 id
     * @return 错误信息字符串
     */
    @GetMapping("/fix/error")
    public BaseResponse<String> getFixError(@RequestParam(name = "appId") Long appId) {
        ThrowUtils.throwIf(appId == null, ErrorCode.PARAMS_ERROR);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        Integer currentVersion = app.getCurrentVersionNum();
        AppVersion version = appVersionService.getByVersionNum(appId, currentVersion);
        ThrowUtils.throwIf(version == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!VersionStatusEnum.NEED_FIX.name().equals(version.getStatus()),
                ErrorCode.OPERATION_ERROR, "当前版本无需修复");
        // 读取 metadata.json 获取 errorLog
        String versionPath = "generated-apps/" + appId + "/v" + currentVersion;
        File metadataFile = new File(versionPath, "metadata.json");
        ThrowUtils.throwIf(!metadataFile.exists(), ErrorCode.SYSTEM_ERROR, "metadata.json 不存在");
        try {
            String content = Files.readString(metadataFile.toPath());
            JSONObject metadata = new JSONObject(content);
            String errorLog = metadata.getStr("errorLog", "");
            return ResultUtils.success(String.format("遇到了下面的 BUG: %s", errorLog));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取 metadata.json 失败");
        }
    }

    @GetMapping("/source/file-tree")
    public BaseResponse<FileTreeNodeVO> getFileTree(@RequestParam(name = "appId") Long appId) {
        return ResultUtils.success(appSourceService.getFileTree(appId));
    }

    @GetMapping("/source/file-content")
    public BaseResponse<FileContentVO> getFileContent(
            @RequestParam(name = "appId") Long appId,
            @RequestParam(name = "filePath") String filePath,
            @RequestParam(name = "start", defaultValue = "0") Integer start,
            @RequestParam(name = "limit", defaultValue = "1000") Integer limit
    ) {
        return ResultUtils.success(appSourceService.getFileContent(appId, filePath, start, limit));
    }

    @GetMapping("/source/files")
    public BaseResponse<FileListVO> getFileList(
            @RequestParam(name = "appId") Long appId,
            @RequestParam(name = "directory", required = false) String directory,
            @RequestParam(name = "recursive", defaultValue = "false") Boolean recursive
    ) {
        return ResultUtils.success(appSourceService.getFileList(appId, directory, recursive));
    }
}
