package com.ai.cs.common.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON 行日志必须带上类名、方法与耗时字段，运维树才能分层排障。
 */
@DisplayName("JsonLineEncoder 结构化字段")
class JsonLineEncoderTest {

    @Test
    @DisplayName("短类名取 logger 最后一段")
    void shortClassTakesLastSegment() {
        assertEquals("AiAgentService", JsonLineEncoder.shortClass("com.ai.cs.aiagent.service.AiAgentService"));
        assertEquals("Foo", JsonLineEncoder.shortClass("Foo"));
        assertEquals("", JsonLineEncoder.shortClass(""));
    }

    @Test
    @DisplayName("编码结果含 logger、耗时与链路字段")
    void encodeIncludesDurationAndTrace() {
        MDC.put("requestId", "req-1");
        MDC.put("traceId", "tr-1");
        MDC.put("durationMs", "42");
        MDC.put("methodName", "chat");
        try {
            LoggingEvent event = new LoggingEvent();
            event.setLoggerName("com.ai.cs.aiagent.service.AiAgentService");
            event.setLevel(Level.INFO);
            event.setMessage("调用对话模型");
            event.setTimeStamp(System.currentTimeMillis());
            event.setMDCPropertyMap(java.util.Map.of(
                    "requestId", "req-1",
                    "traceId", "tr-1",
                    "durationMs", "42",
                    "methodName", "chat"
            ));
            JsonLineEncoder encoder = new JsonLineEncoder();
            encoder.setService("ai-cs-agent");
            String line = new String(encoder.encode(event), StandardCharsets.UTF_8);
            assertTrue(line.contains("\"service\":\"ai-cs-agent\""));
            assertTrue(line.contains("\"requestId\":\"req-1\""));
            assertTrue(line.contains("\"traceId\":\"tr-1\""));
            assertTrue(line.contains("\"durationMs\":42"));
            assertTrue(line.contains("\"logger\":\"com.ai.cs.aiagent.service.AiAgentService\""));
            assertTrue(line.contains("chat") || line.contains("AiAgentService"));
        } finally {
            MDC.clear();
        }
    }
}
