package com.ai.cs.ops.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志分页查询结果视图对象。
 */
@Data
public class OpsLogPageVO {
    /** 当前页日志列表（按时间倒序） */
    private List<OpsLogEntryVO> list = new ArrayList<>();
    /** 命中总数 */
    private long total;
    /** 当前页码（从 1 开始） */
    private int page;
    /** 每页条数 */
    private int size;
    /** 数据源标识（file 等） */
    private String source;
    /** 页面提示文案 */
    private String hint;
}
