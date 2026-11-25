package com.ai.cs.aiagent.dto;

import com.ai.cs.common.dto.ToolConfirmCardDTO;
import lombok.Data;

/**
 * FunctionCallService 的编排结果载体。
 *
 * <p>selected=false 表示模型没选工具，上层继续走 RAG；
 * selected=true 且 confirmCard 非空表示需要人工确认。</p>
 */
@Data
public class ToolCallOutcome {
    private boolean selected;
    private String reply;
    private ToolConfirmCardDTO confirmCard;

    /** 构造 selected=false 的实例：上层据此回退 RAG。 */
    public static ToolCallOutcome none() {
        return new ToolCallOutcome();
    }
}
