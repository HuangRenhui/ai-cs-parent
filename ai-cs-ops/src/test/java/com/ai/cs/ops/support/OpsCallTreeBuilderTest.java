package com.ai.cs.ops.support;

import com.ai.cs.ops.dto.OpsLogEntryVO;
import com.ai.cs.ops.dto.OpsTraceVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 调用树聚合（占位态）：聚合未实现期间，必须保证返回结构完整、不抛异常。
 * 待 {@link OpsCallTreeBuilder} 补齐实现后，本用例应改回「按服务/类/方法分层 + 补耗时」的真实断言。
 */
@DisplayName("OpsCallTreeBuilder 调用树（占位）")
class OpsCallTreeBuilderTest {

    @Test
    @DisplayName("占位：fromLogs 回填 traceId，步骤为空并给出固定文案")
    void fromLogsReturnsEmptyPlaceholder() {
        OpsTraceVO trace = OpsCallTreeBuilder.fromLogs("tr-1", List.of(log("ai-cs-agent", "com.ai.cs.aiagent.service.AiAgentService")));
        assertEquals("tr-1", trace.getTraceId());
        assertTrue(trace.getSpans().isEmpty());
        assertEquals("暂无调用步骤", trace.getShareText());
    }

    @Test
    @DisplayName("占位：groupByTrace 返回空集合")
    void groupByTraceReturnsEmpty() {
        assertTrue(OpsCallTreeBuilder.groupByTrace(List.of(log("gateway", "A"))).isEmpty());
    }

    @Test
    @DisplayName("占位：fillDurations 不改动 durationMs")
    void fillDurationsKeepsOriginalValue() {
        OpsLogEntryVO row = log("gateway", "A");
        row.setDurationMs(null);
        OpsCallTreeBuilder.fillDurations(List.of(row));
        assertNull(row.getDurationMs());
    }

    @Test
    @DisplayName("耗时格式化：空值/毫秒/秒")
    void formatsDuration() {
        assertEquals("—", OpsCallTreeBuilder.formatDuration(null));
        assertEquals("—", OpsCallTreeBuilder.formatDuration(-1L));
        assertEquals("860 毫秒", OpsCallTreeBuilder.formatDuration(860L));
        assertEquals("1.50 秒", OpsCallTreeBuilder.formatDuration(1500L));
    }

    private static OpsLogEntryVO log(String service, String cls) {
        OpsLogEntryVO row = new OpsLogEntryVO();
        row.setService(service);
        row.setClassName(cls);
        return row;
    }
}
