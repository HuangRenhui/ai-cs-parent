package com.ai.cs.common.result;

import lombok.Data;

/**
 * 统一返回结果
 *
 * @author huangrenhui
 * @date 2026/6/11 14:56
 */
@Data
public class Result<T> {
    /** 状态码：200 成功，其余为错误码（与业务异常 code 对应） */
    private Integer code;
    /** 提示信息（面向用户的中文文案） */
    private String msg;
    /** 业务数据 */
    private T data;

    /** 无数据的成功响应 */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 带数据的成功响应 */
    public static <T> Result<T> success(T data) {
        return success("操作成功", data);
    }

    /** 自定义提示的成功响应 */
    public static <T> Result<T> success(String msg, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }

    /** 是否成功（code==200），Feign 调用方判断下游结果用 */
    public boolean isOk() {
        return Integer.valueOf(200).equals(code);
    }

    /** 带错误码的失败响应 */
    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    /** 默认 500 的失败响应 */
    public static <T> Result<T> fail(String msg) {
        return fail(500, msg);
    }
}