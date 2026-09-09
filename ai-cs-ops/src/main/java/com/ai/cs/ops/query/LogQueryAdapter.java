package com.ai.cs.ops.query;

import com.ai.cs.ops.dto.OpsLogEntryVO;
import com.ai.cs.ops.dto.OpsLogPageVO;
import com.ai.cs.ops.dto.OpsLogQuery;

import java.util.List;

/**
 * 日志查询适配器接口：屏蔽底层日志存储差异（本地文件 / Loki / ES），
 * 替换实现时接口形状与上层代码不变。
 */
public interface LogQueryAdapter {

    /**
     * 数据源标识（如 file），展示给前端说明当前日志来源。
     */
    String source();

    /**
     * 页面提示文案：说明当前实现的适用范围与局限。
     */
    String hint();

    /**
     * 多条件组合 + 分页搜索日志。
     */
    OpsLogPageVO search(OpsLogQuery query);

    /**
     * 按请求 ID 串起全链路日志（按时间正序）。
     */
    List<OpsLogEntryVO> byRequestId(String requestId);

    /**
     * 扫描到的日志文件数。
     */
    int fileCount();

    /**
     * 近期 WARN/ERROR 级别日志条数（用于总览大盘）。
     */
    int recentErrorCount();
}
