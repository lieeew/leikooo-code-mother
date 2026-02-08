package com.leikooo.codemother.model.vo;

import cn.hutool.core.bean.BeanUtil;
import com.leikooo.codemother.model.entity.AppVersion;
import com.leikooo.codemother.utils.UuidV7Generator;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author leikooo
 */
@Data
public class AppVersionVO implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 关联的应用 ID
     */
    private Long appId;

    /**
     * 版本号（v0, v1, v2...）
     */
    private Integer versionNum;

    /**
     * 代码 ZIP COS 地址
     */
    private String fileUrl;

    /**
     * 版本状态：SOURCE_BUILDING/BUILDING/SUCCESS/NEED_FIX
     */
    private String status;

    /**
     * 文件数量
     */
    private Integer fileCount;

    /**
     * 总文件大小（字节）
     */
    private Long fileSize;

    /**
     * 创建时间
     */
    private Date createTime;


    /**
     * 操作用户 ID
     */
    private String userId;

    private static final long serialVersionUID = 1L;

    public static AppVersionVO toVO(AppVersion appVersion) {
        AppVersionVO appVersionVO = new AppVersionVO();
        BeanUtil.copyProperties(appVersion, appVersionVO);
        appVersionVO.setUserId(UuidV7Generator.bytesToUuid(appVersion.getUserId()));
        return appVersionVO;
    }
}