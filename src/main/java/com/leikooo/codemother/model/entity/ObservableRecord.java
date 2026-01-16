package com.leikooo.codemother.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.util.Date;

/**
 * @author leikooo
 * @TableName observable_record
 */
@TableName(value = "observable_record")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservableRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "conversation_id")
    private String conversationId;

    @TableField(value = "input_tokens")
    private Long inputTokens;

    @TableField(value = "output_tokens")
    private Long outputTokens;

    @TableField(value = "duration_ms")
    private Long durationMs;

    @TableField(value = "tool_call_count")
    private Integer toolCallCount;

    @TableField(value = "timestamp")
    private Date timestamp;
}
