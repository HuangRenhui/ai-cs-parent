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
    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return success("操作成功", data);
    }

    public static <T> Result<T> success(String msg, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }

    public boolean isOk() {
        return Integer.valueOf(200).equals(code);
    }

    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    public static <T> Result<T> fail(String msg) {
        return fail(500, msg);
    }
}