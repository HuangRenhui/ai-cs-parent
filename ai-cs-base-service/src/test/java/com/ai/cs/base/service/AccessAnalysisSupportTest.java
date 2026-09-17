package com.ai.cs.base.service;

import com.ai.cs.base.entity.OperationLog;
import com.ai.cs.common.dto.AccessAnalysisDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 流量与 IP 聚合（占位实现）：聚合算法待补齐，当前只保证返回空结果且不抛异常、列表不为 null。
 */
@DisplayName("AccessAnalysisSupport")
class AccessAnalysisSupportTest {

    @Test
    @DisplayName("占位实现返回空结果且列表不为 null")
    void placeholderReturnsEmpty() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 11, 20, 10);
        AccessAnalysisDTO dto = AccessAnalysisSupport.from(
                List.of(log("10.0.0.8", 1, t, "auth"), log("10.0.0.8", 0, t.plusMinutes(1), "auth")));
        assertNotNull(dto);
        assertEquals(0, dto.getTotal());
        assertEquals(0, dto.getSuccess());
        assertEquals(0, dto.getFail());
        assertEquals(0, dto.getUniqueIps());
        assertNotNull(dto.getTraffic());
        assertTrue(dto.getTraffic().isEmpty());
        assertNotNull(dto.getTopIps());
        assertTrue(dto.getTopIps().isEmpty());
        assertNotNull(dto.getRiskIps());
        assertTrue(dto.getRiskIps().isEmpty());
    }

    @Test
    @DisplayName("空列表与 null 也返回空结果，不抛异常")
    void emptyRowsReturnsEmpty() {
        assertNotNull(AccessAnalysisSupport.from(List.of()));
        assertNotNull(AccessAnalysisSupport.from(null));
    }

    private static OperationLog log(String ip, int status, LocalDateTime time, String module) {
        OperationLog row = new OperationLog();
        row.setIp(ip);
        row.setStatus(status);
        row.setCreateTime(time);
        row.setModule(module);
        row.setDuration(12L);
        return row;
    }
}
