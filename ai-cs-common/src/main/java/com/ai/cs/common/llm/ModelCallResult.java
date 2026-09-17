package com.ai.cs.common.llm;

import lombok.Data;

import java.io.Serializable;

/**
 * 模型调用结果：文本 + 用量 + 耗时，供路由层记录与计费。
 *
 * @author ai-cs
 */
@Data
public class ModelCallResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 输出文本 */
    private String text;

    /** 输入 token */
    private Integer promptTokens;

    /** 输出 token */
    private Integer completionTokens;

    /** 总 token */
    private Integer totalTokens;

    /** 耗时(毫秒) */
    private Long latencyMs;

    /**
     * 便捷构造调用结果
     *
     * @param text             输出文本（embedding 场景下为向量 JSON 串，由调用方解析）
     * @param promptTokens     输入 token
     * @param completionTokens 输出 token
     * @param totalTokens      总 token
     * @param latencyMs        耗时(毫秒)
     */
    public static ModelCallResult of(String text, Integer promptTokens, Integer completionTokens, Integer totalTokens, Long latencyMs) {
        ModelCallResult r = new ModelCallResult();
        r.setText(text);
        r.setPromptTokens(promptTokens);
        r.setCompletionTokens(completionTokens);
        r.setTotalTokens(totalTokens);
        r.setLatencyMs(latencyMs);
        return r;
    }
}
