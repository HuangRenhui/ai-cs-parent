package com.ai.cs.common.result;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 分页结果包装
 *
 * @author huangrenhui
 */
@Data
public class PageResult<T> {
    /** 当前页数据列表（默认空列表，避免前端拿到 null） */
    private List<T> records = Collections.emptyList();
    /** 总记录数 */
    private long total;
    /** 当前页码（从 1 开始） */
    private long page;
    /** 每页条数 */
    private long size;

    /**
     * 构造分页结果；records 传 null 时兜底为空列表
     */
    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records == null ? Collections.emptyList() : records);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        return result;
    }
}
