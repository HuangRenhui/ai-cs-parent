package com.ai.cs.open.entity;

import com.ai.cs.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 开放工具连接器实体（对应表 cs_open_connector）。
 * 描述如何连接外部业务系统：MOCK 返回演示假数据，REST 发起真实 HTTP 出站调用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cs_open_connector")
public class OpenConnector extends BaseEntity {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 连接器名称 */
    private String name;
    /** MOCK | REST */
    private String type;
    /** REST 连接器基础地址（保存时会做 SSRF 安全校验） */
    private String baseUrl;
    /** 鉴权配置 JSON（预留：密钥/token 等） */
    private String authJson;
    /** 所属行业包 code，空则始终可用 */
    private String packCode;
    /** 1 启用 0 停用 */
    private Integer enabled;
    /** 逻辑删除标记 */
    @TableLogic
    private Integer delFlag;
}
