package com.ai.cs.ops.dto;

import lombok.Data;

/**
 * 单个服务的健康探测结果视图对象。
 */
@Data
public class OpsHealthItemVO {
    /** 服务名 */
    private String name;
    /** 探测地址 */
    private String url;
    /** 探测结果（UP 可达 / DOWN 不可达） */
    private String status;
    /** HTTP 响应码 */
    private Integer httpStatus;
    /** 探测耗时（毫秒） */
    private Long latencyMs;
    /** 附加信息（可达 / HTTP 错误码 / 异常类型） */
    private String message;
}
