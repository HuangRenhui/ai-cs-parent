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
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属租户编码：由服务端按登录令牌写入，客户端传入值不生效。
     * 更新时固定不参与 SET（NEVER），避免改表把坐席「搬」到别的租户。
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String tenantCode;
    /** 坐席登录账号（租户内唯一） */
    private String agentAccount;
    /** 坐席登录密码（BCrypt密文；NOT_NULL 策略避免更新时被 null 覆盖） */
    @TableField(updateStrategy = FieldStrategy.NOT_NULL)
    private String agentPwd;
    /** 坐席姓名/昵称 */
    private String agentName;
    /** 坐席状态：0-离线 1-在线 2-忙碌 */
    private Integer agentStatus;
    /** 逻辑删除标记：0-正常 1-已删除 */
    @TableLogic
    private Integer delFlag;
}
