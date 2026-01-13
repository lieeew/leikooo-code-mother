package com.leikooo.codemother.model.dto.request.app;

import com.leikooo.codemother.commen.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/1/13
 * @description
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AppQueryRequest extends PageRequest implements Serializable {

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

}
