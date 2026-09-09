package com.ai.cs.ops.query;

import com.ai.cs.ops.dto.OpsLogEntryVO;
import com.ai.cs.ops.dto.OpsLogQuery;
import com.ai.cs.ops.dto.OpsTracePageVO;
import com.ai.cs.ops.dto.OpsTraceSpanVO;
import com.ai.cs.ops.dto.OpsTraceVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基于本地日志文件的链路查询适配器（POC 实现）。
 * 尚未接入 Zipkin/SkyWalking，按日志中的 traceId 把日志聚合成"伪链路"，仅作排障线索。
 */
@Component
public class FileTraceQueryAdapter implements TraceQueryAdapter {

    @Resource
    private FileLogQueryAdapter fileLogQueryAdapter;

    /**
     * 分页查询链路：先从日志适配器拉取最多 200 条命中日志，按 traceId 聚合成链路后再内存分页。
     */
    @Override
    public OpsTracePageVO list(String traceId, String sessionId, Integer page, Integer size) {
        // 页码与页大小兜底：页码最小 1，页大小限制在 1~200
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 50 : Math.min(Math.max(size, 1), 200);
        // 链路聚合依赖日志检索，先按条件取日志（固定取前 200 条，避免全量加载）
        OpsLogQuery query = new OpsLogQuery();
        query.setTraceId(traceId);
        query.setSessionId(sessionId);
        query.setPage(1);
        query.setSize(200);
        List<OpsTraceVO> traces = group(fileLogQueryAdapter.search(query).getList());
        OpsTracePageVO pageVO = new OpsTracePageVO();
        pageVO.setSource("file");
        pageVO.setHint("尚未接入 Zipkin/SkyWalking。当前按日志中的 traceId 聚合，仅作排障线索。");
        pageVO.setTotal(traces.size());
        pageVO.setPage(safePage);
        pageVO.setSize(safeSize);
        int from = Math.min((safePage - 1) * safeSize, traces.size());
        int to = Math.min(from + safeSize, traces.size());
        pageVO.setList(traces.subList(from, to));
        return pageVO;
    }

    /**
     * 查询单条链路详情：复用列表查询，未命中时返回只有 traceId 的空对象，避免前端空指针。
     */
    @Override
    public OpsTraceVO detail(String traceId) {
        OpsTracePageVO page = list(traceId, null, 1, 1);
        if (page.getList().isEmpty()) {
            OpsTraceVO empty = new OpsTraceVO();
            empty.setTraceId(traceId);
            return empty;
        }
        return page.getList().get(0);
    }

    /**
     * 把日志条目按 traceId 聚合成链路：
     * 同一 traceId 下先按时间排序，再按服务分组生成 Span（首/末条日志时间作为 Span 起止），
     * 最终按链路开始时间倒序返回。
     */
    private static List<OpsTraceVO> group(List<OpsLogEntryVO> logs) {
        // 按 traceId 分组，LinkedHashMap 保持日志原始顺序
        Map<String, List<OpsLogEntryVO>> grouped = logs.stream()
                .filter(item -> StringUtils.hasText(item.getTraceId()))
                .collect(Collectors.groupingBy(OpsLogEntryVO::getTraceId, LinkedHashMap::new, Collectors.toList()));
        List<OpsTraceVO> traces = new ArrayList<>();
        for (Map.Entry<String, List<OpsLogEntryVO>> entry : grouped.entrySet()) {
            // 链路内日志按时间正序，首末条即链路起止时间
            List<OpsLogEntryVO> items = entry.getValue().stream()
                    .sorted(Comparator.comparing(OpsLogEntryVO::getTimestamp, Comparator.nullsLast(String::compareTo)))
                    .toList();
            OpsTraceVO trace = new OpsTraceVO();
            trace.setTraceId(entry.getKey());
            trace.setSessionId(items.stream().map(OpsLogEntryVO::getSessionId).filter(StringUtils::hasText).findFirst().orElse(""));
            trace.setStartTime(items.get(0).getTimestamp());
            trace.setEndTime(items.get(items.size() - 1).getTimestamp());
            // 再按服务分组，一个服务一个 Span（无服务名的归入 unknown）
            Map<String, List<OpsLogEntryVO>> byService = items.stream()
                    .collect(Collectors.groupingBy(item -> Objects.toString(item.getService(), "unknown"), LinkedHashMap::new, Collectors.toList()));
            List<OpsTraceSpanVO> spans = new ArrayList<>();
            byService.forEach((service, serviceLogs) -> {
                OpsTraceSpanVO span = new OpsTraceSpanVO();
                span.setSpanId(service);
                span.setService(service);
                span.setName("logs");
                span.setStartTime(serviceLogs.get(0).getTimestamp());
                span.setEndTime(serviceLogs.get(serviceLogs.size() - 1).getTimestamp());
                span.setRequestId(serviceLogs.stream().map(OpsLogEntryVO::getRequestId).filter(StringUtils::hasText).findFirst().orElse(""));
                spans.add(span);
            });
            trace.setSpans(spans);
            trace.setSpanCount(spans.size());
            traces.add(trace);
        }
        // 最新的链路排前面，符合排障场景先看新近调用
        traces.sort(Comparator.comparing(OpsTraceVO::getStartTime, Comparator.nullsLast(String::compareTo)).reversed());
        return traces;
    }
}
