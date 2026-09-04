package com.ai.cs.ops.query;

import com.ai.cs.ops.dto.OpsLogEntryVO;
import com.ai.cs.ops.dto.OpsLogPageVO;
import com.ai.cs.ops.dto.OpsLogQuery;

import java.util.List;

public interface LogQueryAdapter {

    String source();

    String hint();

    OpsLogPageVO search(OpsLogQuery query);

    List<OpsLogEntryVO> byRequestId(String requestId);

    int fileCount();

    int recentErrorCount();
}
