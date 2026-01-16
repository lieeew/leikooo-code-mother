package com.leikooo.codemother.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.util.Date;

/**
 * 
 * @author leikooo
 * @TableName spring_ai_chat_memory
 */
@TableName(value ="spring_ai_chat_memory")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpringAiChatMemory implements Serializable {
    /**
     * 
     */
    @TableField(value = "conversation_id")
    private String conversationId;

    /**
     * 
     */
    @TableField(value = "content")
    private String content;

    /**
     * 
     */
    @TableField(value = "type")
    private Object type;

    /**
     * 
     */
    @TableField(value = "timestamp")
    private Date timestamp;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}