package com.ai.cs.ops.query;

import com.ai.cs.ops.dto.OpsLogQuery;
import com.ai.cs.ops.dto.OpsTracePageVO;
import com.ai.cs.ops.dto.OpsTraceVO;
import com.ai.cs.ops.support.OpsCallTreeBuilder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于本地日志文件的链路查询适配器（POC 实现）。
 * 尚未接入 Zipkin/SkyWalking，按日志中的 traceId 聚成「服务 → 类 → 方法」调用树。
 */
@Component
public class FileTraceQueryAdapter implements TraceQueryAdapter {

    @Resource
    private FileLogQueryAdapter fileLogQueryAdapter;

    /**
     * 分页查询链路：先从日志适配器拉取最多 200 条命中日志，按 traceId 聚合成树后再内存分页。
     */
    @Override
    public OpsTracePageVO list(String traceId, String sessionId, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 50 : Math.min(Math.max(size, 1), 200);
        OpsLogQuery query = new OpsLogQuery();
        query.setTraceId(traceId);
        query.setSessionId(sessionId);
        query.setPage(1);
        query.setSize(200);
        List<OpsTraceVO> traces = OpsCallTreeBuilder.groupByTrace(fileLogQueryAdapter.search(query).getList());
        OpsTracePageVO pageVO = new OpsTracePageVO();
        pageVO.setSource("file");
        pageVO.setHint("尚未接入 Zipkin/SkyWalking。当前按日志聚成服务/类/方法树，带耗时与排障方向，便于复制分享。");
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
            empty.setShareText("暂无调用步骤");
            return empty;
        }
        return page.getList().get(0);
    }
}
