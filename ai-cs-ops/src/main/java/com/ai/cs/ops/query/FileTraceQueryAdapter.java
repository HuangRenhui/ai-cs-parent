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

@Component
public class FileTraceQueryAdapter implements TraceQueryAdapter {

    @Resource
    private FileLogQueryAdapter fileLogQueryAdapter;

    @Override
    public OpsTracePageVO list(String traceId, String sessionId, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 50 : Math.min(Math.max(size, 1), 200);
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

    private static List<OpsTraceVO> group(List<OpsLogEntryVO> logs) {
        Map<String, List<OpsLogEntryVO>> grouped = logs.stream()
                .filter(item -> StringUtils.hasText(item.getTraceId()))
                .collect(Collectors.groupingBy(OpsLogEntryVO::getTraceId, LinkedHashMap::new, Collectors.toList()));
        List<OpsTraceVO> traces = new ArrayList<>();
        for (Map.Entry<String, List<OpsLogEntryVO>> entry : grouped.entrySet()) {
            List<OpsLogEntryVO> items = entry.getValue().stream()
                    .sorted(Comparator.comparing(OpsLogEntryVO::getTimestamp, Comparator.nullsLast(String::compareTo)))
                    .toList();
            OpsTraceVO trace = new OpsTraceVO();
            trace.setTraceId(entry.getKey());
            trace.setSessionId(items.stream().map(OpsLogEntryVO::getSessionId).filter(StringUtils::hasText).findFirst().orElse(""));
            trace.setStartTime(items.get(0).getTimestamp());
            trace.setEndTime(items.get(items.size() - 1).getTimestamp());
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
        traces.sort(Comparator.comparing(OpsTraceVO::getStartTime, Comparator.nullsLast(String::compareTo)).reversed());
        return traces;
    }
}
