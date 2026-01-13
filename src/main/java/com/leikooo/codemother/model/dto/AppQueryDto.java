package com.leikooo.codemother.model.dto;

import cn.hutool.core.bean.BeanUtil;
import com.leikooo.codemother.commen.PageRequest;
import com.leikooo.codemother.model.dto.request.app.AppQueryRequest;
import com.leikooo.codemother.model.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/1/13
 * @description
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppQueryDto extends PageRequest implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * appName
     */
    private String appName;

    /**
     * 初始化 prompt
     */
    private String initPrompt;

    /**
     * 代码生成类型（枚举）
     */
    private String codeGenType;

    /**
     * 部署后的唯一标识
     */
    private String deployKey;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * userId
     */
    private String userId;

    public static AppQueryDto toDto(AppQueryRequest appQueryRequest, UserVO userVO) {
        AppQueryDto appQueryDto = new AppQueryDto();
        BeanUtil.copyProperties(appQueryRequest, appQueryDto);
        appQueryDto.setUserId(userVO.getId());
        return appQueryDto;
    }
}
