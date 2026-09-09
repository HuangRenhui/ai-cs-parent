package com.ai.cs.common.dto;

import lombok.Data;

/**
 * 开放工具调用结果
 *
 * @author ai-cs
 */
@Data
public class ToolInvokeResultDTO {
    /** 是否执行成功 */
    private boolean success;
    /** 工具名（回显，便于调用方核对） */
    private String toolName;
    /** 工具输出（成功为结果文本，失败为错误说明） */
    private String output;
    /** 结果来源（如 real/mock/cache），便于排查演示数据与真实数据 */
    private String source;
}
