package com.ai.cs.common.result;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class PageResult<T> {
    private List<T> records = Collections.emptyList();
    private long total;
    private long page;
    private long size;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records == null ? Collections.emptyList() : records);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        return result;
    }
}
