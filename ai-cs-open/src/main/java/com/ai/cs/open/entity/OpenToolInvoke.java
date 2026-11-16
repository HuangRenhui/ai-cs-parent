package com.ai.cs.open.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放工具调用记录实体（对应表 cs_open_tool_invoke）。
 *
 * <p>一张表承担三个职责：</p>
 * <ol>
 *   <li><b>人工确认</b>：CONFIRMING 行即待办，确认后同一行继续走到 SUCCEEDED/FAILED，审计连续；</li>
 *   <li><b>幂等占位</b>：带幂等键的请求先落一行占位，靠唯一索引挡住并发重复调用；</li>
 *   <li><b>调用审计</b>：request/response + 发起人 + 确认人 + 时间。</li>
 * </ol>
 *
 * <p><b>关键约束</b>：CONFIRMING 行的 {@code idempotencyKey} 必须为 null——
 * 未确认不占幂等键，MySQL 允许多个 NULL 并存，正好承载该语义。</p>
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
    /** 租户编码（所有查询必须带此口径） */
    private String tenantCode;
    /** 状态：CONFIRMING/CONFIRMED/SUCCEEDED/FAILED/REJECTED/EXPIRED/CANCELLED */
    private String status;
    /** 发起时风险等级快照（事后工具配置变更也不影响审计口径） */
    private String risk;
    /** 发起方标识：坐席用户名 / 访客 ref / ai */
    private String requestedBy;
    /** 确认人：只从 JWT 取，禁止请求体传入 */
    private String confirmedBy;
    /** 确认时间 */
    private LocalDateTime confirmedAt;
    /** 拒绝理由 */
    private String rejectedReason;
    /** 失效时间；空表示不过期 */
    private LocalDateTime expireAt;
    /** 请求参数 JSON */
    private String requestJson;
    /** 响应内容 JSON / 失败原因 */
    private String responseJson;
    /** 幂等键（调用方传入或确认时生成，表上建有唯一约束） */
    private String idempotencyKey;
    /** 调用结果标记：1 成功、0 处理中或失败 */
    private Integer success;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
