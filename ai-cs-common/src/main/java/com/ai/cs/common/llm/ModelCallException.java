package com.ai.cs.common.llm;

/**
 * 模型调用异常：模型服务不可用、返回结构异常、配置缺失等场景抛出。
 * 由全局异常处理器统一转成 503 友好提示。
 *
 * @author ai-cs
 */
public class ModelCallException extends RuntimeException {
    public ModelCallException(String message) {
        super(message);
    }

    public ModelCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
