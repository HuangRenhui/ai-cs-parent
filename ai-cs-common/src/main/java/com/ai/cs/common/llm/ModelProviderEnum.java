package com.ai.cs.common.llm;

/**
 * 模型供应方枚举：本地(OLLAMA) 与在线(DASHSCOPE/OPENAI/DEEPSEEK/OTHER)
 *
 * @author ai-cs
 */
public enum ModelProviderEnum {
    OLLAMA("ollama", "本地 Ollama"),
    DASHSCOPE("dashscope", "阿里云 DashScope"),
    OPENAI("openai", "OpenAI 兼容"),
    DEEPSEEK("deepseek", "DeepSeek"),
    OTHER("other", "其他");

    private final String code;
    private final String label;

    ModelProviderEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ModelProviderEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (ModelProviderEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }
}
