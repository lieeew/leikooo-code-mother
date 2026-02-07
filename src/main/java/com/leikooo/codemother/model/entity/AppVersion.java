package com.leikooo.codemother.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 应用版本记录
 * @TableName app_version
 */
@TableName(value ="app_version")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppVersion implements Serializable {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的应用 ID
     */
    @TableField(value = "appId")
    private Long appId;

    /**
     * 版本号（v0, v1, v2...）
     */
    @TableField(value = "versionNum")
    private Integer versionNum;

    /**
     * 代码 ZIP COS 地址
     */
    @TableField(value = "fileUrl")
    private String fileUrl;

    /**
     * 版本状态：SOURCE_BUILDING/BUILDING/SUCCESS/NEED_FIX
     */
    @TableField(value = "status")
    private String status;

    /**
     * 文件数量
     */
    @TableField(value = "fileCount")
    private Integer fileCount;

    /**
     * 总文件大小（字节）
     */
    @TableField(value = "fileSize")
    private Long fileSize;

    /**
     * 创建时间
     */
    @TableField(value = "createTime")
    private Date createTime;

    /**
     * 是否删除
     */
    @TableField(value = "isDelete")
    private Integer isDelete;

    /**
     * 操作用户 ID
     */
    @TableField(value = "userId")
    private byte[] userId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}