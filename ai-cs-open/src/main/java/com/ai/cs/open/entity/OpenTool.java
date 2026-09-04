package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_open_tool")
public class OpenTool extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String inputSchema;
    /** read | write | critical */
    private String risk;
    private Long connectorId;
    private String httpMethod;
    private String httpPath;
    private Integer timeoutMs;
    /** 绑定当前演示意图名，如 查物流、退款；正式环境应改为租户配置 */
    private String intentBind;
    /** 所属行业包；包关闭后该工具不可调用 */
    private String packCode;
    @TableLogic
    private Integer delFlag;
}
