package com.leikooo.codemother.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.leikooo.codemother.model.entity.AppVersion;

import java.util.List;

/**
 * @author leikooo
 * @description 针对表【app_version(应用版本记录)】的数据库操作Service
 * @createDate 2026-02-04 23:52:49
 */
public interface AppVersionService extends IService<AppVersion> {

    /**
     * 获取应用版本列表（按版本号倒序）
     * @param appId 应用ID
     * @return 版本列表
     */
    List<AppVersion> listByAppId(Long appId);

    /**
     * 获取应用最新版本号
     * @param appId 应用ID
     * @return 最新版本号，不存在返回0
     */
    int getMaxVersionNum(Long appId);

    /**
     * 根据版本号获取版本
     * @param appId 应用ID
     * @param versionNum 版本号
     * @return 版本记录
     */
    AppVersion getByVersionNum(Long appId, Integer versionNum);

    /**
     * 初始化 v0 版本（首次创建 App 时调用）
     * @param appId 应用ID
     */
    void initVersion(Long appId);

    /**
     * 回滚到指定版本
     * @param appId 应用ID
     * @param versionNum 版本号
     */
    void rollback(Long appId, Integer versionNum);

    /**
     * 保存新版本
     * @param appId 应用ID
     * @return 新版本号
     */
    Integer saveVersion(String appId);

    /**
     * 更新构建状态
     * @param appId 应用ID
     */
    void updateBuildStatus(String appId);
}
