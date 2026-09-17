package com.ai.cs.ops.support;

import com.ai.cs.ops.dto.OpsLogEntryVO;
import com.ai.cs.ops.dto.OpsTraceVO;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;

/**
 * 调用树构建工具（占位）。
 *
 * <p>TODO 后续实现：把同一 traceId 的日志按「服务 → 类 → 方法」聚合成树，
 * 计算各节点耗时范围、标注慢方法与异常级别的排障方向，并生成可复制的分享文本。
 * 当前所有聚合与推断方法均为空实现，链路页只显示「暂无调用步骤」。</p>
 */
@Slf4j
public class OpsCallTreeBuilder {

    private OpsCallTreeBuilder() {
    }

    /**
     * 按 traceId 聚合日志为调用树（占位：返回空集合）。
     */
    public static List<OpsTraceVO> groupByTrace(List<OpsLogEntryVO> logs) {
        log.info("[占位] 调用树按 traceId 聚合未实现，返回空集合");
        return List.of();
    }

    /**
     * 单条 traceId 的日志聚合为一条链路（占位：仅回填 traceId）。
     */
    public static OpsTraceVO fromLogs(String traceId, List<OpsLogEntryVO> raw) {
        log.info("[占位] 调用树聚合未实现 traceId={}", traceId);
        OpsTraceVO trace = new OpsTraceVO();
        trace.setTraceId(traceId);
        trace.setSpans(List.of());
        trace.setShareText("暂无调用步骤");
        return trace;
    }

    /**
     * 用相邻日志时间戳补齐缺失的 durationMs（占位：不做任何处理）。
     */
    public static void fillDurations(List<OpsLogEntryVO> logs) {
        log.info("[占位] 相邻日志补耗时未实现");
    }

    /**
     * 生成链路分享文本（占位：返回固定文案）。
     */
    public static String shareText(OpsTraceVO trace, String requestId) {
        return "暂无调用步骤";
    }

    /**
     * 生成单节点的排障方向提示（占位：返回空串）。
     */
    public static String analysis(String level, Long durationMs, String name, String message) {
        return "";
    }

    /**
     * 耗时格式化：小于 1 秒显示毫秒，否则显示两位小数的秒；空值与负数显示「—」。
     */
    public static String formatDuration(Long ms) {
        if (ms == null || ms < 0) {
            return "—";
        }
        if (ms < 1000) {
            return ms + " 毫秒";
        }
        return String.format(Locale.ROOT, "%.2f 秒", ms / 1000.0);
    }

    /**
     * 取日志的类名（占位）。
     */
    static String classOf(OpsLogEntryVO log) {
        return null;
    }

    /**
     * 取日志的方法名（占位）。
     */
    static String methodOf(OpsLogEntryVO log) {
        return null;
    }

    /**
     * 由服务名与日志正文推断类名（占位）。
     */
    static String inferClass(String service, String message) {
        return null;
    }

    /**
     * 由日志正文推断方法名（占位）。
     */
    static String inferMethod(String message) {
        return null;
    }

    /**
     * 取类的简单名（占位）。
     */
    static String simpleName(String className) {
        return null;
    }
}
