package com.ai.cs.base.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Data
@TableName("cs_operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String operation;
    private String method;
    private String requestUrl;
    private String requestMethod;
    private String requestParams;
    private String responseResult;
    private String ip;
    private Long duration;
    private Integer status;
    private String errorMsg;
    private LocalDateTime createTime;
}
