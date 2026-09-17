package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 开放工具实体（对应表 cs_open_tool）。
 * 注册可被 AI Agent / 前端调用的业务工具，绑定连接器执行，
 * 按风险等级（read/write/critical）决定是否需要人工确认。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_open_tool")
public class OpenTool extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 工具名（如 apply_refund、query_account） */
    private String name;
    /** 工具描述（供模型选择工具时参考） */
    private String description;
    /** 入参 JSON Schema */
    private String inputSchema;
    /** read | write | critical */
    private String risk;
    /** 绑定的连接器 ID */
    private Long connectorId;
    /** REST 调用的 HTTP 方法（缺省 POST） */
    private String httpMethod;
    /** REST 调用的相对路径 */
    private String httpPath;
    /** 调用超时毫秒数（缺省 5000，最小 500） */
    private Integer timeoutMs;
    /** 绑定当前演示意图名，如 查物流、退款；正式环境应改为租户配置 */
    private String intentBind;
    /** 所属行业包；包关闭后该工具不可调用 */
    private String packCode;
    /** 逻辑删除标记 */
    @TableLogic
    private Integer delFlag;
}
