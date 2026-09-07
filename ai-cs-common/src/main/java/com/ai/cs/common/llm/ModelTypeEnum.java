package com.ai.cs.common.llm;

/**
 * 模型能力类型：对话/向量/重排/视觉/多模态
 *
 * @author ai-cs
 */
public enum ModelTypeEnum {
    LLM("LLM", "对话"),
    EMBEDDING("EMBEDDING", "向量"),
    RERANK("RERANK", "重排"),
    VISION("VISION", "视觉"),
    MULTIMODAL("MULTIMODAL", "多模态");

    private final String code;
    private final String label;

    ModelTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ModelTypeEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (ModelTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }
}
