package com.ai.cs.ops.query;

import com.ai.cs.ops.dto.OpsLogEntryVO;
import com.ai.cs.ops.dto.OpsLogPageVO;
import com.ai.cs.ops.dto.OpsLogQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本地滚动日志文件查询适配器（占位）。
 *
 * <p>TODO 后续实现：扫描配置目录与工作目录下的 *.log，兼容「普通文本行」与「JSON 行」两种格式，
 * 支持多条件过滤与内存分页，并在返回前统一经 {@code LogRedactor} 脱敏。
 * 当前检索恒返回空结果，总览大盘的错误条数与文件数恒为 0。</p>
 */
@Slf4j
@Component
public class FileLogQueryAdapter implements LogQueryAdapter {

    @Override
    public String source() {
        return "file";
    }

    @Override
    public String hint() {
        return "当前为本地滚动日志适配器（单机/POC）。接上 Loki/ES 后只换适配器，接口形状不变。";
    }

    /**
     * 多条件分页搜索日志（占位：返回空页，仅回显分页参数）。
     * 页码最小 1，页大小限制在 1~200。
     */
    @Override
    public OpsLogPageVO search(OpsLogQuery query) {
        log.info("[占位] 日志检索未实现，返回空结果");
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int size = query.getSize() == null ? 50 : Math.min(Math.max(query.getSize(), 1), 200);
        OpsLogPageVO pageVO = new OpsLogPageVO();
        pageVO.setSource(source());
        pageVO.setHint(hint());
        pageVO.setTotal(0);
        pageVO.setPage(page);
        pageVO.setSize(size);
        pageVO.setList(List.of());
        return pageVO;
    }

    /**
     * 按请求 ID 查询全链路日志（占位：返回空集合）。
     */
    @Override
    public List<OpsLogEntryVO> byRequestId(String requestId) {
        log.info("[占位] 按 requestId 串查日志未实现 requestId={}", requestId);
        return List.of();
    }

    /**
     * 扫描到的日志文件数（占位：恒为 0）。
     */
    @Override
    public int fileCount() {
        return 0;
    }

    /**
     * 近期 WARN/ERROR 级别日志条数（占位：恒为 0）。
     */
    @Override
    public int recentErrorCount() {
        return 0;
    }
}
