package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 连接器字段映射实体（对应表 cs_connector_field_mapping）。
 * 用于映射对方系统字段与客服内核实体字段，如对方 orderNo ↔ entity.id。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_connector_field_mapping")
public class ConnectorFieldMapping extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 连接器 ID */
    private Long connectorId;
    /** 对方系统字段名（如 orderNo） */
    private String sourceField;
    /** 内核实体字段名（如 entity.id） */
    private String targetField;
    /** 字段类型（string/number/date/json） */
    private String fieldType;
    /** 转换规则 JSON（如日期格式、枚举映射） */
    private String transformRule;
    /** 是否必填 */
    private Integer required;
    /** 默认值 */
    private String defaultValue;
    /** 备注说明 */
    private String remark;
    /** 逻辑删除标记 */
    @TableLogic
    private Integer delFlag;
}
