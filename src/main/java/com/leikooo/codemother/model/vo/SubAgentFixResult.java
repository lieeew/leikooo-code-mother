package com.leikooo.codemother.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SubAgent 修复结果对象
 * 用于主 Agent 感知 SubAgent 修复状态
 *
 * @author leikooo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentFixResult {
    
    /**
     * 应用 ID
     */
    private Long appId;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 总尝试次数
     */
    private int totalAttempts;
    
    /**
     * 修复摘要
     */
    private String summary;
    
    /**
     * AI 修复过程内容
     */
    private String aiContent;
    
    /**
     * 最后一次错误日志（如果失败）
     */
    private String lastErrorLog;
    
    /**
     * 完成时间
     */
    private LocalDateTime completedTime;
    
    /**
     * SubAgent 实例 ID
     */
    private String subAgentId;
}
