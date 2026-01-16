package com.leikooo.codemother.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * @author leikooo
 * @TableName tool_call_record
 */
@TableName(value ="spring_tool_call_record")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallRecord implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "sessionId")
    private String sessionId;

    @TableField(value = "toolCallId")
    private String toolCallId;

    @TableField(value = "className")
    private String className;

    @TableField(value = "methodName")
    private String methodName;

    @TableField(value = "callType")
    private String callType;

    @TableField(value = "result")
    private String result;

    @TableField(value = "createTime")
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
