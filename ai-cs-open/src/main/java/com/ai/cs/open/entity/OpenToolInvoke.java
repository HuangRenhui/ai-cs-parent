package com.ai.cs.open.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放工具调用记录实体（对应表 cs_open_tool_invoke）。
 * 同时承担「幂等占位」与「调用审计」两个职责：
 * 带幂等键的请求先落一行占位记录，靠唯一索引挡住并发重复调用。
 */
@Data
@TableName("cs_open_tool_invoke")
public class OpenToolInvoke {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 工具名 */
    private String toolName;
    /** 发起调用的会话 ID */
    private String sessionId;
    /** 请求参数 JSON */
    private String requestJson;
    /** 响应内容 JSON */
    private String responseJson;
    /** 幂等键（调用方传入，表上建有唯一约束） */
    private String idempotencyKey;
    /** 调用结果标记：1 成功、0 处理中或失败、-1 并发占用（仅内存态，不落库） */
    private Integer success;
    /** 创建时间 */
    private LocalDateTime createTime;
}
