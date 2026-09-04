package com.ai.cs.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WidgetInitDTO {
    private String tenantCode;
    private String channel;
    private String visitorRef;
    private String scene;
    private List<BizEntity> entities = new ArrayList<>();
    private String locale;
    private Long customerId;
}
