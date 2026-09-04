package com.ai.cs.common.llm;

public class ModelCallException extends RuntimeException {
    public ModelCallException(String message) {
        super(message);
    }

    public ModelCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
