package com.ai.cs.ops.query;

import com.ai.cs.ops.dto.OpsTracePageVO;
import com.ai.cs.ops.dto.OpsTraceVO;

/**
 * 链路查询适配器接口：屏蔽底层实现差异（本地日志聚合 / Zipkin / SkyWalking），
 * 后续接入专业链路系统时只换实现类，接口形状与上层代码不变。
 */
public interface TraceQueryAdapter {

    /**
     * 分页查询链路列表。
     *
     * @param traceId   按链路 ID 过滤（可空）
     * @param sessionId 按会话 ID 过滤（可空）
     * @param page      页码，从 1 开始
     * @param size      每页条数
     */
    OpsTracePageVO list(String traceId, String sessionId, Integer page, Integer size);

    /**
     * 查询单条链路详情。
     */
    OpsTraceVO detail(String traceId);
}
