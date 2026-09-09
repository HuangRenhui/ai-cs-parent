package com.ai.cs.common.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import com.alibaba.fastjson.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 结构化 JSON 行日志，供运维台按 requestId/sessionId 检索。
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
        // new JSONObject(true) 保持字段插入序
        JSONObject json = new JSONObject(true);
        json.put("ts", FMT.format(Instant.ofEpochMilli(event.getTimeStamp()).atZone(ZoneId.systemDefault())));
        json.put("level", event.getLevel().toString());
        json.put("service", service);
        json.put("logger", event.getLoggerName());
        json.put("msg", event.getFormattedMessage());
        // 链路字段从 MDC 取，缺失补空串，保证每行字段齐全
        Map<String, String> mdc = event.getMDCPropertyMap();
        json.put("requestId", mdc.getOrDefault("requestId", ""));
        json.put("traceId", mdc.getOrDefault("traceId", ""));
        json.put("sessionId", mdc.getOrDefault("sessionId", ""));
        json.put("tenantId", mdc.getOrDefault("tenantId", ""));
        return (json.toJSONString() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] footerBytes() {
        return new byte[0];
    }
}
