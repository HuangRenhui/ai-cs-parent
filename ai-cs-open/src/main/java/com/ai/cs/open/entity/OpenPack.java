package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_open_pack")
public class OpenPack extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** ecommerce / finance / retail / custom */
    private String code;
    private String name;
    private String remark;
    /** 1 启用：该包下工具可被调用 */
    private Integer enabled;
    private Integer sortNum;
}
