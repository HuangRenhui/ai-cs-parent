package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OpenAPI 导入记录实体（对应表 cs_openapi_import）。
 * 记录从 Swagger/OpenAPI 规范导入的工具列表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_openapi_import")
public class OpenApiImport extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 导入名称 */
    private String importName;
    /** OpenAPI/Swagger 文档 URL 或内容 */
    private String sourceUrl;
    /** 导入状态（pending/success/failed） */
    private String status;
    /** 导入的工具数量 */
    private Integer toolCount;
    /** 错误信息 */
    private String errorMsg;
    /** 绑定的连接器 ID */
    private Long connectorId;
    /** 所属行业包 */
    private String packCode;
    /** 所属租户编码 */
    private String tenantCode;
    /** 备注说明 */
    private String remark;
    /** 逻辑删除标记 */
    @TableLogic
    private Integer delFlag;
}
