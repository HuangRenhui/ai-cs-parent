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
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 操作用户ID */
    private Long userId;
    /** 操作用户名 */
    private String username;
    /** 所属业务模块 */
    private String module;
    /** 操作描述 */
    private String operation;
    /** 调用的 Java 方法（类名.方法名） */
    private String method;
    /** 请求URL */
    private String requestUrl;
    /** 请求方式（GET/POST等） */
    private String requestMethod;
    /** 请求参数（JSON 串） */
    private String requestParams;
    /** 响应结果（JSON 串，可能被截断） */
    private String responseResult;
    /** 操作者IP */
    private String ip;
    /** 接口耗时（毫秒） */
    private Long duration;
    /** 执行状态：1-成功 0-失败 */
    private Integer status;
    /** 失败时的错误信息 */
    private String errorMsg;
    /** 操作时间 */
    private LocalDateTime createTime;
}
