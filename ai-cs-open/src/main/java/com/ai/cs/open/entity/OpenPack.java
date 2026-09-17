package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 行业包实体（对应表 cs_open_pack）。
 * 按行业（电商/金融/零售等）对开放工具分组，支持整体启停与热切换，
 * 内核不写死行业逻辑，行业差异全部沉淀在行业包配置中。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_open_pack")
public class OpenPack extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** ecommerce / finance / retail / custom */
    private String code;
    /** 行业包名称 */
    private String name;
    /** 备注说明 */
    private String remark;
    /** 1 启用：该包下工具可被调用 */
    private Integer enabled;
    /** 排序号，越小越靠前 */
    private Integer sortNum;
}
