package com.ai.cs.common.exception;

import lombok.Getter;

/**
 * 业务异常：由全局异常处理器统一捕获并转成 {@code Result.fail} 返回前端。
 * message 面向用户，应使用友好中文提示。
 *
 * @author huangrenhui
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码（默认 400） */
    private final Integer code;

    /** 默认 400 业务异常 */
    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
