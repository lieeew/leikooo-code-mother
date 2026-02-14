package com.leikooo.codemother.model.vo;

import cn.hutool.core.bean.BeanUtil;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.utils.UuidV7Generator;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用封装类
 * @author leikooo
 */
@Getter
@Setter
public class AppVO implements Serializable {

    /**
     * 主键
     */
    private String id;

    /**
     * appName
     */
    private String appName;

    /**
     * 封面地址
     */
    private String cover;

    /**
     * 代码生成
     */
    private String codeGenType;

    /**
     * 初始化 prompt
     */
    private String initPrompt;

    /**
     * 部署后的唯一标识
     */
    private String deployKey;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建用户 ID
     */
    private String userId;

    /**
     * 生成 APP 输入 token
     */
    private Long totalInputTokens;

    /**
     * 生成 APP 输出 token
     */
    private Long totalOutputTokens;

    /**
     * 生成 APP 消耗时间
     */
    private Long totalConsumeTime;

    /**
     * 创建用户信息
     */
    private UserVO user;

    /**
     * 当前版本
     */
    private Integer currentVersionNum;

    private static final long serialVersionUID = 1L;

    public static AppVO toVO(App app, UserVO userVO) {
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        if (app.getId() != null) {
            appVO.setId(String.valueOf(app.getId()));
        }
        if (app.getUserId() != null) {
            appVO.setUserId(UuidV7Generator.bytesToUuid(app.getUserId()));
        }
        appVO.setUser(userVO);
        return appVO;
    }
}