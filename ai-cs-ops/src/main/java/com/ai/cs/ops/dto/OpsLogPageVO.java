package com.ai.cs.ops.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OpsLogPageVO {
    private List<OpsLogEntryVO> list = new ArrayList<>();
    private long total;
    private int page;
    private int size;
    private String source;
    private String hint;
}
