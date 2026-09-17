package com.ai.cs.common.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import com.alibaba.fastjson2.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 结构化 JSON 行日志，供运维台按 requestId/sessionId 检索，并带类名、方法、耗时便于排障。
 */
public class JsonLineEncoder extends EncoderBase<ILoggingEvent> {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** 服务名标识（logback 配置可注入，用于多服务日志聚合时区分来源） */
    private String service = "ai-cs";

    /** logback 配置注入入口：空白值忽略，保留默认 */
    public void setService(String service) {
        if (service != null && !service.isBlank()) {
            this.service = service.trim();
        }
    }

    @Override
    public byte[] headerBytes() {
        return new byte[0];
    }

    /** 把一条日志事件编码为一行 JSON（字段顺序固定，便于检索与比对） */
    @Override
    public byte[] encode(ILoggingEvent event) {
        JSONObject json = new JSONObject();
        json.put("ts", FMT.format(Instant.ofEpochMilli(event.getTimeStamp()).atZone(ZoneId.systemDefault())));
        json.put("level", event.getLevel().toString());
        json.put("service", service);
        json.put("logger", event.getLoggerName());
        json.put("thread", event.getThreadName());
        json.put("msg", event.getFormattedMessage());
        // 链路字段从 MDC 取，缺失补空串，保证每行字段齐全
        Map<String, String> mdc = event.getMDCPropertyMap();
        json.put("requestId", mdc.getOrDefault("requestId", ""));
        json.put("traceId", mdc.getOrDefault("traceId", ""));
        json.put("sessionId", mdc.getOrDefault("sessionId", ""));
        json.put("tenantId", mdc.getOrDefault("tenantId", ""));
        // 类名/方法名：优先调用栈里业务包帧，其次 MDC，再次用 logger 短名
        fillCaller(json, event, mdc);
        // 单步耗时（毫秒）：过滤器在请求结束时写入 MDC，聚合时也可按相邻日志补齐
        String duration = mdc.get("durationMs");
        if (duration != null && !duration.isBlank()) {
            try {
                json.put("durationMs", Long.parseLong(duration.trim()));
            } catch (NumberFormatException ignored) {
                // 非法耗时不阻断日志落盘
            }
        }
        return (json.toJSONString() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 写入 className / methodName / line：排障时能直接指到具体方法，而不是只看到服务名。
     */
    private static void fillCaller(JSONObject json, ILoggingEvent event, Map<String, String> mdc) {
        String className = firstNonBlank(mdc.get("className"), shortClass(event.getLoggerName()));
        String methodName = firstNonBlank(mdc.get("methodName"), mdc.get("method"));
        Integer line = null;
        try {
            StackTraceElement[] caller = event.getCallerData();
            if (caller != null) {
                for (StackTraceElement el : caller) {
                    // 跳过日志框架自身，落到业务代码帧
                    if (el.getClassName() != null && el.getClassName().startsWith("com.ai.cs")) {
                        className = el.getClassName();
                        methodName = el.getMethodName();
                        line = el.getLineNumber();
                        break;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 取调用栈失败时退回 logger / MDC，不影响日志写出
        }
        json.put("className", className == null ? "" : className);
        json.put("methodName", methodName == null ? "" : methodName);
        if (line != null && line > 0) {
            json.put("line", line);
        }
    }

    /** 取 logger 最后一段作为短类名，例如 com.ai.cs.x.Foo → Foo */
    static String shortClass(String logger) {
        if (logger == null || logger.isBlank()) {
            return "";
        }
        int i = logger.lastIndexOf('.');
        return i >= 0 ? logger.substring(i + 1) : logger;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return "";
    }

    @Override
    public byte[] footerBytes() {
        return new byte[0];
    }
}
