package com.ai.cs.ops.query;

import com.ai.cs.ops.dto.OpsTracePageVO;
import com.ai.cs.ops.dto.OpsTraceVO;

public interface TraceQueryAdapter {

    OpsTracePageVO list(String traceId, String sessionId, Integer page, Integer size);

    OpsTraceVO detail(String traceId);
}
