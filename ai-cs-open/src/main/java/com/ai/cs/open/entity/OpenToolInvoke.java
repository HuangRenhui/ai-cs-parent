package com.ai.cs.open.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_open_tool_invoke")
public class OpenToolInvoke {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String toolName;
    private String sessionId;
    private String requestJson;
    private String responseJson;
    private String idempotencyKey;
    private Integer success;
    private LocalDateTime createTime;
}
