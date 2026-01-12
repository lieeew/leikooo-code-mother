package com.leikooo.codemother.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

/**
 * App
 * @author leikooo
 * @TableName app
 */
@TableName(value = "app")
@Data
public class
App implements Serializable {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * appName
     */
    @TableField(value = "appName")
    private String appName;

    /**
     * 封面地址
     */
    @TableField(value = "cover")
    private String cover;

    /**
     * 初始化 prompt
     */
    @TableField(value = "initPrompt")
    private String initPrompt;

    /**
     * 代码生成类型（枚举）
     */
    @TableField(value = "codeGenType")
    private String codeGenType;

    /**
     * 部署后的唯一标识
     */
    @TableField(value = "deployKey")
    private String deployKey;

    /**
     * 编辑时间
     */
    @TableField(value = "editTime")
    private Date editTime;

    /**
     * 创建时间
     */
    @TableField(value = "createTime")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "updateTime")
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    @TableField(value = "isDelete")
    private Integer isDelete;

    /**
     * 创建用户 ID
     */
    @TableField(value = "userId")
    private byte[] userId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}