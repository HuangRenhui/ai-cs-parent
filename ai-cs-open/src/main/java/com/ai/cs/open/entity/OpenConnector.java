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
@TableName("cs_open_connector")
public class OpenConnector extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** MOCK | REST */
    private String type;
    private String baseUrl;
    private String authJson;
    /** 所属行业包 code，空则始终可用 */
    private String packCode;
    private Integer enabled;
    @TableLogic
    private Integer delFlag;
}
