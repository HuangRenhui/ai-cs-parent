package com.ai.cs.base.entity;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 18:11
 * @description 坐席实体
 */
import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("cs_agent")
public class Agent extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentAccount;
    @TableField(updateStrategy = FieldStrategy.NOT_NULL)
    private String agentPwd;
    private String agentName;
    private Integer agentStatus;
    @TableLogic
    private Integer delFlag;
}
