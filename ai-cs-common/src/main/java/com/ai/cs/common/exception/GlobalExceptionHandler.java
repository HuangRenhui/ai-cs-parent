package com.ai.cs.common.exception;

import com.ai.cs.common.llm.ModelCallException;
import com.ai.cs.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局统一异常处理
 *
 * @author huangrenhui
 * @date 2026/6/11 18:01
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：按异常自带 code 返回（默认 400），message 直接透传给前端 */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        int code = e.getCode() == null ? 400 : e.getCode();
        return Result.fail(code, e.getMessage());
    }

    /** 模型调用异常：HTTP 503，对外隐藏具体模型错误细节，只给友好提示 */
    @ExceptionHandler(ModelCallException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<?> handleModelCall(ModelCallException e) {
        log.warn("模型调用失败：{}", e.getMessage());
        return Result.fail(503, "模型服务暂时不可用，请稍后重试");
    }

    /** 请求参数类异常：参数缺失、类型不匹配、非法参数，统一 400 */
    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBadRequest(Exception e) {
        log.warn("请求参数异常: {}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    /** Bean Validation 校验失败：取第一个字段错误信息返回（校验注解上已写好中文提示） */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValid(Exception e) {
        String msg = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException manv && manv.getBindingResult().getFieldError() != null) {
            msg = manv.getBindingResult().getFieldError().getDefaultMessage();
        } else if (e instanceof BindException be && be.getBindingResult().getFieldError() != null) {
            msg = be.getBindingResult().getFieldError().getDefaultMessage();
        }
        return Result.fail(400, msg);
    }

    /** 兜底：未预期的系统异常，记完整堆栈，对外只给通用提示（不泄露内部细节） */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        log.error("系统异常：", e);
        return Result.fail("系统繁忙，请稍后重试");
    }
}
