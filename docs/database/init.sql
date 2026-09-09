-- ============================================
-- AI 智能客服系统 - 全量初始化
-- 库名: ai_customer_service
-- 新建库只执行本文件。禁止在此加订单 / 银行卡 / 门店表。
-- ============================================

CREATE DATABASE IF NOT EXISTS ai_customer_service
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;

USE ai_customer_service;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 业务表
-- ============================================
DROP TABLE IF EXISTS `cs_chat_msg`;
DROP TABLE IF EXISTS `cs_chat_session`;
DROP TABLE IF EXISTS `cs_work_order`;
DROP TABLE IF EXISTS `cs_knowledge_miss`;
DROP TABLE IF EXISTS `cs_knowledge_faq`;
DROP TABLE IF EXISTS `cs_knowledge_graph_relation`;
DROP TABLE IF EXISTS `cs_knowledge_graph_node`;
DROP TABLE IF EXISTS `cs_multimodal_knowledge`;
DROP TABLE IF EXISTS `cs_document_version`;
DROP TABLE IF EXISTS `cs_open_tool_invoke`;
DROP TABLE IF EXISTS `cs_open_tool`;
DROP TABLE IF EXISTS `cs_open_connector`;
DROP TABLE IF EXISTS `cs_open_pack`;
DROP TABLE IF EXISTS `cs_scene_config`;
DROP TABLE IF EXISTS `cs_visitor_map`;
DROP TABLE IF EXISTS `cs_customer`;
DROP TABLE IF EXISTS `cs_agent`;
DROP TABLE IF EXISTS `cs_role_menu`;
DROP TABLE IF EXISTS `cs_user_role`;
DROP TABLE IF EXISTS `cs_user`;
DROP TABLE IF EXISTS `cs_role`;
DROP TABLE IF EXISTS `cs_menu`;
DROP TABLE IF EXISTS `cs_operation_log`;
DROP TABLE IF EXISTS `cs_statistics`;
DROP TABLE IF EXISTS `cs_ai_model`;
DROP TABLE IF EXISTS `cs_model_usage`;
DROP TABLE IF EXISTS `cs_sys_config`;
DROP TABLE IF EXISTS `cs_intent_config`;
DROP TABLE IF EXISTS `cs_slot_filling`;
DROP TABLE IF EXISTS `cs_data_retention`;

CREATE TABLE `cs_customer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '客户ID',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `nickname` VARCHAR(100) DEFAULT NULL COMMENT '昵称',
    `gender` TINYINT DEFAULT NULL COMMENT '性别: 1-男, 2-女, 空-未选',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `customer_tag` VARCHAR(200) DEFAULT NULL COMMENT '客户标签',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_phone` (`phone`),
    KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户信息表';

CREATE TABLE `cs_agent` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '坐席ID',
    `agent_account` VARCHAR(50) NOT NULL COMMENT '坐席账号',
    `agent_pwd` VARCHAR(100) NOT NULL COMMENT '坐席密码(加密)',
    `agent_name` VARCHAR(50) NOT NULL COMMENT '坐席姓名',
    `agent_status` TINYINT DEFAULT 0 COMMENT '坐席状态: 0-离线, 1-在线, 2-忙碌',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_account` (`agent_account`),
    KEY `idx_agent_status` (`agent_status`),
    KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='坐席信息表';

CREATE TABLE `cs_chat_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `session_id` VARCHAR(64) NOT NULL COMMENT '会话唯一标识',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `agent_id` BIGINT DEFAULT NULL COMMENT '坐席ID',
    `session_type` TINYINT DEFAULT 1 COMMENT '会话类型: 1-AI客服, 2-人工客服',
    `session_status` TINYINT DEFAULT 1 COMMENT '会话状态: 1-进行中, 2-已结束',
    `start_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

CREATE TABLE `cs_chat_msg` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `msg_content` TEXT NOT NULL COMMENT '消息内容',
    `msg_type` TINYINT DEFAULT 1 COMMENT '发送方: 1-用户, 2-AI, 3-坐席（非图片/文件类型）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

CREATE TABLE `cs_knowledge_faq` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'FAQ ID',
    `tenant_code` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户编码',
    `question` VARCHAR(500) NOT NULL COMMENT '问题',
    `answer` TEXT NOT NULL COMMENT '答案',
    `category` VARCHAR(100) DEFAULT NULL COMMENT '分类',
    `sort_num` INT DEFAULT 0 COMMENT '排序号',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `milvus_id` VARCHAR(100) DEFAULT NULL COMMENT 'Milvus向量数据库中的记录ID',
    `audit_status` TINYINT DEFAULT 2 COMMENT '审核状态: 0-草稿, 1-待审核, 2-已发布, 3-已下线',
    `like_count` INT DEFAULT 0 COMMENT '点赞数',
    `dislike_count` INT DEFAULT 0 COMMENT '点踩数',
    `view_count` INT DEFAULT 0 COMMENT '浏览数',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_code` (`tenant_code`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`),
    KEY `idx_milvus_id` (`milvus_id`),
    KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库FAQ表';

CREATE TABLE `cs_knowledge_miss` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '未命中ID',
    `tenant_code` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户编码',
    `question` VARCHAR(500) NOT NULL COMMENT '用户问题',
    `session_id` VARCHAR(64) DEFAULT NULL COMMENT '会话ID',
    `top_score` DECIMAL(8,4) DEFAULT NULL COMMENT '最高相似度（未过阈值）',
    `status` TINYINT DEFAULT 0 COMMENT '处理状态: 0-待处理, 1-已转问',
    `faq_id` BIGINT DEFAULT NULL COMMENT '转问生成的FAQ ID',
    `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_miss_tenant_time` (`tenant_code`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库未命中回收';

CREATE TABLE `cs_work_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '工单ID',
    `order_no` VARCHAR(50) NOT NULL COMMENT '工单编号',
    `session_id` VARCHAR(64) DEFAULT NULL COMMENT '关联会话ID',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `order_type` VARCHAR(50) DEFAULT NULL COMMENT '工单类型',
    `order_content` TEXT NOT NULL COMMENT '工单内容',
    `order_status` TINYINT DEFAULT 1 COMMENT '工单状态: 1-待处理, 2-处理中, 3-已完成, 4-已关闭',
    `agent_id` BIGINT DEFAULT NULL COMMENT '处理坐席ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_order_status` (`order_status`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单表';

CREATE TABLE `cs_document_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `document_id` VARCHAR(64) NOT NULL COMMENT '文档唯一标识（文件名或文档ID）',
    `document_name` VARCHAR(256) NOT NULL COMMENT '文档名称',
    `version` INT NOT NULL COMMENT '版本号',
    `file_path` VARCHAR(512) NOT NULL COMMENT '文件路径',
    `file_size` BIGINT DEFAULT 0 COMMENT '文件大小（字节）',
    `file_type` VARCHAR(32) DEFAULT NULL COMMENT '文件类型（PDF/TXT/DOCX/MD等）',
    `file_md5` VARCHAR(64) DEFAULT NULL COMMENT '文件MD5哈希值，用于内容去重',
    `milvus_collection_id` VARCHAR(128) DEFAULT NULL COMMENT 'Milvus向量集合ID',
    `segment_count` INT DEFAULT 0 COMMENT '文档片段数量',
    `version_description` VARCHAR(512) DEFAULT NULL COMMENT '版本描述',
    `is_current` TINYINT DEFAULT 0 COMMENT '是否为当前版本: 0-否, 1-是',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-草稿, 1-已发布, 2-已归档',
    `uploader_id` BIGINT DEFAULT NULL COMMENT '上传人ID',
    `uploader_name` VARCHAR(64) DEFAULT NULL COMMENT '上传人姓名',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_document_version` (`document_id`, `version`),
    KEY `idx_document_id` (`document_id`),
    KEY `idx_is_current` (`is_current`),
    KEY `idx_file_md5` (`file_md5`),
    KEY `idx_status` (`status`),
    KEY `idx_del_flag` (`del_flag`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档版本管理表';

CREATE TABLE `cs_knowledge_graph_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `node_id` VARCHAR(64) DEFAULT NULL COMMENT '节点唯一标识',
    `name` VARCHAR(256) DEFAULT NULL COMMENT '实体名称',
    `label` VARCHAR(64) DEFAULT NULL COMMENT '节点类型',
    `properties` TEXT DEFAULT NULL COMMENT '额外属性 JSON',
    `description` TEXT DEFAULT NULL COMMENT '描述',
    `source_document_id` VARCHAR(64) DEFAULT NULL COMMENT '来源文档ID',
    `source_document_name` VARCHAR(256) DEFAULT NULL COMMENT '来源文档名称',
    `importance` INT DEFAULT NULL COMMENT '重要性 0-100',
    `is_core` TINYINT DEFAULT 0 COMMENT '是否核心节点',
    `status` TINYINT DEFAULT 1 COMMENT '0-草稿, 1-已发布, 2-已归档',
    `del_flag` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_graph_node_id` (`node_id`),
    KEY `idx_graph_node_label` (`label`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱节点（半成品，默认未启用图库）';

CREATE TABLE `cs_knowledge_graph_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `relation_id` VARCHAR(64) DEFAULT NULL COMMENT '关系唯一标识',
    `source_node_id` VARCHAR(64) DEFAULT NULL COMMENT '源节点ID',
    `target_node_id` VARCHAR(64) DEFAULT NULL COMMENT '目标节点ID',
    `source_node_name` VARCHAR(256) DEFAULT NULL,
    `target_node_name` VARCHAR(256) DEFAULT NULL,
    `relation_type` VARCHAR(64) DEFAULT NULL COMMENT '关系类型',
    `description` TEXT DEFAULT NULL,
    `properties` TEXT DEFAULT NULL COMMENT '额外属性 JSON',
    `weight` INT DEFAULT NULL COMMENT '权重 0-100',
    `confidence` DOUBLE DEFAULT NULL COMMENT '置信度 0-1',
    `source_document_id` VARCHAR(64) DEFAULT NULL,
    `status` TINYINT DEFAULT 1 COMMENT '0-草稿, 1-已发布, 2-已归档',
    `del_flag` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_graph_rel_src` (`source_node_id`),
    KEY `idx_graph_rel_tgt` (`target_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱关系（半成品）';

CREATE TABLE `cs_multimodal_knowledge` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `knowledge_id` VARCHAR(64) DEFAULT NULL COMMENT '条目唯一标识',
    `modality` VARCHAR(16) DEFAULT NULL COMMENT 'IMAGE / AUDIO / VIDEO / MIXED',
    `resource_path` VARCHAR(512) DEFAULT NULL,
    `resource_id` VARCHAR(64) DEFAULT NULL,
    `title` VARCHAR(256) DEFAULT NULL,
    `description` TEXT DEFAULT NULL,
    `analysis` TEXT DEFAULT NULL COMMENT 'AI 分析文本',
    `keywords` TEXT DEFAULT NULL COMMENT '关键词 JSON',
    `entities` TEXT DEFAULT NULL COMMENT '实体 JSON',
    `tags` VARCHAR(500) DEFAULT NULL,
    `source_document_id` VARCHAR(64) DEFAULT NULL,
    `vector_id` VARCHAR(128) DEFAULT NULL,
    `vector_collection` VARCHAR(128) DEFAULT NULL,
    `confidence` DOUBLE DEFAULT NULL,
    `status` TINYINT DEFAULT 1 COMMENT '0-处理中, 1-已入库, 2-已归档',
    `del_flag` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_mm_knowledge_id` (`knowledge_id`),
    KEY `idx_mm_modality` (`modality`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多模态知识条目（半成品）';

CREATE TABLE `cs_open_connector` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL COMMENT '连接器名称',
    `type` VARCHAR(16) NOT NULL COMMENT 'MOCK | REST',
    `base_url` VARCHAR(500) DEFAULT NULL COMMENT 'REST 根地址',
    `auth_json` VARCHAR(1000) DEFAULT NULL COMMENT '鉴权配置 JSON',
    `pack_code` VARCHAR(32) DEFAULT NULL COMMENT '所属行业包',
    `enabled` TINYINT DEFAULT 1,
    `del_flag` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_open_connector_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务系统连接器';

CREATE TABLE `cs_open_tool` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL COMMENT '工具名，如 query_logistics',
    `description` VARCHAR(500) DEFAULT NULL,
    `input_schema` TEXT DEFAULT NULL,
    `risk` VARCHAR(16) DEFAULT 'read' COMMENT 'read | write | critical',
    `connector_id` BIGINT NOT NULL,
    `http_method` VARCHAR(16) DEFAULT 'POST',
    `http_path` VARCHAR(255) DEFAULT NULL,
    `timeout_ms` INT DEFAULT 5000,
    `intent_bind` VARCHAR(32) DEFAULT NULL COMMENT '临时绑定演示意图名',
    `pack_code` VARCHAR(32) DEFAULT NULL COMMENT '所属行业包',
    `del_flag` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_open_tool_name` (`name`),
    KEY `idx_open_tool_intent` (`intent_bind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可被机器人/坐席调用的工具';

CREATE TABLE `cs_open_tool_invoke` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tool_name` VARCHAR(64) DEFAULT NULL,
    `session_id` VARCHAR(64) DEFAULT NULL,
    `request_json` TEXT,
    `response_json` TEXT,
    `idempotency_key` VARCHAR(128) DEFAULT NULL COMMENT '幂等键',
    `success` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_open_invoke_session` (`session_id`),
    UNIQUE KEY `uk_open_invoke_idem` (`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具调用审计';

CREATE TABLE `cs_visitor_map` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_code` VARCHAR(64) NOT NULL DEFAULT 'default',
    `channel` VARCHAR(32) NOT NULL DEFAULT 'web',
    `visitor_ref` VARCHAR(128) NOT NULL COMMENT '对方系统用户 ID',
    `customer_id` BIGINT DEFAULT NULL,
    `scene` VARCHAR(64) DEFAULT NULL,
    `entities_json` TEXT COMMENT '打开聊窗时的实体上下文',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_visitor` (`tenant_code`, `channel`, `visitor_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部用户与客服客户映射';

CREATE TABLE `cs_open_pack` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(32) NOT NULL COMMENT 'ecommerce / finance / retail / custom',
    `name` VARCHAR(64) NOT NULL,
    `remark` VARCHAR(255) DEFAULT NULL,
    `enabled` TINYINT DEFAULT 0 COMMENT '1 启用，该包工具可被调用',
    `sort_num` INT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_open_pack_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行业包（插拔开关）';

CREATE TABLE `cs_scene_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `scene` VARCHAR(64) NOT NULL COMMENT '场景编码: ORDER/PRODUCT/AFTER_SALE/GENERAL 等',
    `scene_name` VARCHAR(64) NOT NULL COMMENT '场景名称',
    `greeting` VARCHAR(500) DEFAULT NULL COMMENT '开场白模板，支持 {entityId} 占位符',
    `greeting_empty` VARCHAR(500) DEFAULT NULL COMMENT '无实体时的开场白',
    `quick_actions` TEXT COMMENT '快捷动作 JSON 数组: [{label, send}]',
    `enabled` TINYINT DEFAULT 1 COMMENT '1 启用 0 停用',
    `sort_num` INT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scene_config_scene` (`scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话入口场景配置';

-- ============================================
-- RBAC / 日志 / 统计 / 配置
-- ============================================
CREATE TABLE `cs_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(200) NOT NULL COMMENT '密码(BCrypt加密)',
    `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `gender` TINYINT DEFAULT 0 COMMENT '性别: 0-未知, 1-男, 2-女',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip` VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_email` (`email`),
    KEY `idx_phone` (`phone`),
    KEY `idx_status` (`status`),
    KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

CREATE TABLE `cs_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
    `sort_num` INT DEFAULT 0 COMMENT '排序号',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

CREATE TABLE `cs_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父菜单ID',
    `menu_name` VARCHAR(50) NOT NULL COMMENT '菜单名称',
    `menu_type` TINYINT DEFAULT 1 COMMENT '菜单类型: 1-目录, 2-菜单, 3-按钮',
    `path` VARCHAR(200) DEFAULT NULL COMMENT '路由路径',
    `component` VARCHAR(200) DEFAULT NULL COMMENT '组件路径',
    `perms` VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
    `icon` VARCHAR(50) DEFAULT NULL COMMENT '菜单图标',
    `sort_num` INT DEFAULT 0 COMMENT '排序号',
    `visible` TINYINT DEFAULT 1 COMMENT '是否可见: 0-隐藏, 1-可见',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单权限表';

CREATE TABLE `cs_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

CREATE TABLE `cs_role_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联表';

CREATE TABLE `cs_operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '操作用户ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '操作用户名',
    `module` VARCHAR(50) DEFAULT NULL COMMENT '操作模块',
    `operation` VARCHAR(50) DEFAULT NULL COMMENT '操作类型',
    `method` VARCHAR(200) DEFAULT NULL COMMENT '请求方法',
    `request_url` VARCHAR(500) DEFAULT NULL COMMENT '请求URL',
    `request_method` VARCHAR(10) DEFAULT NULL COMMENT '请求方式',
    `request_params` TEXT DEFAULT NULL COMMENT '请求参数',
    `response_result` TEXT DEFAULT NULL COMMENT '响应结果',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT '操作IP',
    `duration` BIGINT DEFAULT 0 COMMENT '执行时长(毫秒)',
    `status` TINYINT DEFAULT 1 COMMENT '操作状态: 0-失败, 1-成功',
    `error_msg` TEXT DEFAULT NULL COMMENT '错误信息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

CREATE TABLE `cs_statistics` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '统计ID',
    `stat_date` DATE NOT NULL COMMENT '统计日期',
    `stat_type` VARCHAR(50) NOT NULL COMMENT '统计类型: chat/customer/workorder/knowledge',
    `stat_key` VARCHAR(100) DEFAULT NULL COMMENT '统计维度键',
    `stat_value` DECIMAL(20,2) DEFAULT 0.00 COMMENT '统计值',
    `stat_count` BIGINT DEFAULT 0 COMMENT '统计数量',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stat` (`stat_date`, `stat_type`, `stat_key`),
    KEY `idx_stat_date` (`stat_date`),
    KEY `idx_stat_type` (`stat_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据统计表';

CREATE TABLE `cs_sys_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT NOT NULL COMMENT '配置值',
    `config_type` VARCHAR(50) DEFAULT 'string' COMMENT '配置类型: string/number/boolean/json',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '配置描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

CREATE TABLE `cs_intent_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_code` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户编码',
    `intent_code` VARCHAR(64) NOT NULL COMMENT '意图编码',
    `intent_name` VARCHAR(100) NOT NULL COMMENT '意图名称',
    `intent_type` VARCHAR(32) DEFAULT 'business' COMMENT '意图类型: business/system/transfer',
    `description` TEXT DEFAULT NULL COMMENT '意图描述',
    `keywords` TEXT DEFAULT NULL COMMENT '关键词JSON数组',
    `examples` TEXT DEFAULT NULL COMMENT '示例话术JSON数组',
    `tool_bind` VARCHAR(64) DEFAULT NULL COMMENT '绑定的工具名称',
    `response_template` TEXT DEFAULT NULL COMMENT '回复模板',
    `priority` INT DEFAULT 0 COMMENT '优先级',
    `enabled` TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_intent` (`tenant_code`, `intent_code`),
    KEY `idx_tenant_code` (`tenant_code`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可配置意图表';

CREATE TABLE `cs_slot_filling` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_code` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户编码',
    `intent_code` VARCHAR(64) NOT NULL COMMENT '关联意图编码',
    `slot_name` VARCHAR(64) NOT NULL COMMENT '槽位名称',
    `slot_type` VARCHAR(32) NOT NULL COMMENT '槽位类型: string/number/date/phone/order_id',
    `required` TINYINT DEFAULT 1 COMMENT '是否必填: 0-否, 1-是',
    `prompt_template` TEXT DEFAULT NULL COMMENT '追问话术模板',
    `validation_regex` VARCHAR(500) DEFAULT NULL COMMENT '验证正则表达式',
    `extract_prompt` TEXT DEFAULT NULL COMMENT '提取提示词',
    `priority` INT DEFAULT 0 COMMENT '优先级',
    `enabled` TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_intent` (`tenant_code`, `intent_code`),
    KEY `idx_slot_name` (`slot_name`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多轮填槽配置表';

CREATE TABLE `cs_data_retention` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_code` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户编码',
    `data_type` VARCHAR(32) NOT NULL COMMENT '数据类型: session/message/knowledge/tool_invoke',
    `retention_days` INT NOT NULL DEFAULT 90 COMMENT '保留天数',
    `allow_external_domain` TINYINT DEFAULT 0 COMMENT '是否允许出域: 0-否, 1-是',
    `allow_user_delete` TINYINT DEFAULT 1 COMMENT '是否允许用户删除: 0-否, 1-是',
    `anonymize_after_days` INT DEFAULT NULL COMMENT '匿名化天数',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_type` (`tenant_code`, `data_type`),
    KEY `idx_tenant_code` (`tenant_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据保留策略表';

CREATE TABLE `cs_ai_model` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `model_name` VARCHAR(100) NOT NULL COMMENT '显示名称',
    `provider` VARCHAR(32) NOT NULL COMMENT '供应方: ollama/dashscope/openai/deepseek/other',
    `model_type` VARCHAR(32) NOT NULL COMMENT '能力: LLM/EMBEDDING/RERANK/VISION/MULTIMODAL',
    `base_url` VARCHAR(500) DEFAULT NULL COMMENT '服务地址',
    `api_key` VARCHAR(1000) DEFAULT NULL COMMENT '密钥(AES-GCM加密存储 enc: 前缀)',
    `api_secret` VARCHAR(1000) DEFAULT NULL COMMENT '附加密钥(AES-GCM加密存储)',
    `remote_model` VARCHAR(100) DEFAULT NULL COMMENT '上游模型标识',
    `temperature` DECIMAL(4,2) DEFAULT 0.30 COMMENT '温度',
    `dimension` INT DEFAULT 1024 COMMENT 'embedding维度',
    `priority` INT DEFAULT 0 COMMENT '故障切换优先级(越小越优先)',
    `timeout_ms` INT DEFAULT 20000 COMMENT '单次调用超时(毫秒)',
    `max_retries` INT DEFAULT 1 COMMENT '同模型失败重试次数(超时重试可能重复计费，慎用)',
    `fail_threshold` INT DEFAULT 2 COMMENT '连续失败熔断阈值(达到即摘除)',
    `daily_token_limit` BIGINT DEFAULT NULL COMMENT '每日token配额(NULL不限)',
    `daily_cost_limit` DECIMAL(12,4) DEFAULT NULL COMMENT '每日成本配额(元，NULL不限)',
    `cost_per_1k_in` DECIMAL(10,6) DEFAULT NULL COMMENT '输入单价(元/千token)',
    `cost_per_1k_out` DECIMAL(10,6) DEFAULT NULL COMMENT '输出单价(元/千token)',
    `enabled` TINYINT DEFAULT 1 COMMENT '是否启用: 1是0否',
    `is_active` TINYINT DEFAULT 0 COMMENT '当前生效(同能力仅一条): 1是0否',
    `health` VARCHAR(16) DEFAULT 'UNKNOWN' COMMENT 'UNKNOWN/HEALTHY/DOWN',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    KEY `idx_model_type` (`model_type`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型注册表';

CREATE TABLE `cs_model_usage` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `model_id` BIGINT DEFAULT NULL COMMENT '模型ID',
    `model_name` VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    `model_type` VARCHAR(32) DEFAULT NULL COMMENT '能力类型',
    `provider` VARCHAR(32) DEFAULT NULL COMMENT '供应方',
    `session_id` VARCHAR(64) DEFAULT NULL COMMENT '会话ID',
    `prompt_tokens` INT DEFAULT 0 COMMENT '输入token',
    `completion_tokens` INT DEFAULT 0 COMMENT '输出token',
    `total_tokens` INT DEFAULT 0 COMMENT '总token',
    `latency_ms` INT DEFAULT 0 COMMENT '耗时(毫秒)',
    `cost` DECIMAL(12,6) DEFAULT NULL COMMENT '本次调用成本快照(元，按发生时单价)',
    `success` TINYINT DEFAULT 1 COMMENT '是否成功: 1是0否',
    `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    KEY `idx_model` (`model_id`),
    KEY `idx_type_time` (`model_type`, `create_time`),
    KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型调用用量记录';

-- ============================================
-- 演示种子（生产勿用明文密码）
-- ============================================
INSERT INTO `cs_agent` (`agent_account`, `agent_pwd`, `agent_name`, `agent_status`) VALUES
('admin', '123456', '管理员', 1),
('agent001', '123456', '客服小张', 1),
('agent002', '123456', '客服小李', 0);

INSERT INTO `cs_customer` (`phone`, `nickname`, `customer_tag`) VALUES
('13800138001', '测试用户1', 'VIP'),
('13800138002', '测试用户2', '普通'),
('13800138003', '测试用户3', '新用户');

INSERT INTO `cs_knowledge_faq` (`tenant_code`, `question`, `answer`, `category`, `sort_num`, `status`) VALUES
('default', '如何重置密码？', '您可以在登录页面点击"忘记密码"，然后通过手机号验证码重置密码。', '账户管理', 1, 1),
('default', '如何联系客服？', '您可以通过在线客服、电话400-xxx-xxxx或邮件support@example.com联系我们。', '联系方式', 2, 1),
('default', '退款政策是什么？', '我们支持7天无理由退款，请在订单详情页申请退款。', '售后服务', 3, 1),
('default', '如何修改个人信息？', '登录后进入"个人中心"-"账户设置"即可修改个人信息。', '账户管理', 4, 1),
('default', '支持哪些支付方式？', '我们支持支付宝、微信支付、银行卡等多种支付方式。', '支付问题', 5, 1);

INSERT INTO `cs_open_pack` (`code`, `name`, `remark`, `enabled`, `sort_num`) VALUES
('ecommerce', '电商', '查单/物流/退款类工具', 1, 1),
('finance', '金融', '查账等只读工具；冻卡不自动执行', 0, 2),
('retail', '新零售', '会员/门店/预约', 0, 3);

INSERT INTO `cs_scene_config` (`scene`, `scene_name`, `greeting`, `greeting_empty`, `quick_actions`, `enabled`, `sort_num`) VALUES
('ORDER', '订单入口', '您好，看到您正在咨询订单 {entityId}。您可以点击下方按钮快速操作，或直接描述问题。', '您好，您正在咨询订单相关服务。请提供订单号，或点击下方按钮。', '[{"label":"查看订单情况","send":"帮我查一下这个订单的情况"},{"label":"查询物流","send":"查询物流进度"},{"label":"申请退款","send":"我要申请退款"},{"label":"转人工","send":"转人工"}]', 1, 1),
('PRODUCT', '产品入口', '您好，您正在了解产品 {entityId}。我可以为您介绍详细信息、价格与优惠，也可以直接回答您的疑问。', '您好，欢迎咨询我们的产品。请告诉我您想了解哪款产品，或直接提问。', '[{"label":"了解产品信息","send":"我想了解这个产品的详细信息"},{"label":"价格与优惠","send":"这个产品的价格和优惠活动"},{"label":"转人工","send":"转人工"}]', 1, 2),
('AFTER_SALE', '售后入口', '您好，这里是售后通道。若需对订单 {entityId} 申请退款/售后，请点击下方按钮或描述问题。', '您好，这里是售后通道。请提供需要售后的订单号，或点击下方按钮。', '[{"label":"申请退款","send":"我要申请退款"},{"label":"查询售后进度","send":"查询售后处理进度"},{"label":"转人工","send":"转人工"}]', 1, 3),
('GENERAL', '通用入口', '您好，我是智能客服。可咨询问题；物流/退款会走已注册的连接器，而不是内核订单表。', NULL, '[]', 1, 99);

INSERT INTO `cs_open_connector` (`name`, `type`, `enabled`, `pack_code`) VALUES
('demo-mock', 'MOCK', 1, 'ecommerce'),
('finance-mock', 'MOCK', 1, 'finance');

INSERT INTO `cs_open_tool` (`name`, `description`, `risk`, `connector_id`, `intent_bind`, `pack_code`)
SELECT 'query_logistics', '按实体查询进度（演示，非订单表）', 'read', id, '查物流', 'ecommerce'
FROM `cs_open_connector` WHERE `name` = 'demo-mock';

INSERT INTO `cs_open_tool` (`name`, `description`, `risk`, `connector_id`, `intent_bind`, `pack_code`)
SELECT 'apply_refund', '提交退款类动作到对方系统（演示）', 'write', id, '退款', 'ecommerce'
FROM `cs_open_connector` WHERE `name` = 'demo-mock';

INSERT INTO `cs_open_tool` (`name`, `description`, `risk`, `connector_id`, `intent_bind`, `pack_code`)
SELECT 'query_account', '查询账户状态（金融演示，脱敏）', 'read', id, NULL, 'finance'
FROM `cs_open_connector` WHERE `name` = 'finance-mock';

INSERT INTO `cs_role` (`role_name`, `role_code`, `description`, `sort_num`) VALUES
('超级管理员', 'SUPER_ADMIN', '系统超级管理员，拥有所有权限', 1),
('管理员', 'ADMIN', '系统管理员，拥有大部分管理权限', 2),
('坐席', 'AGENT', '客服坐席，拥有工单处理和客户管理权限', 3),
('普通用户', 'USER', '普通用户，仅拥有基本查看权限', 4);

INSERT INTO `cs_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort_num`) VALUES
(0, '系统管理', 1, '/system', NULL, NULL, 'Setting', 1),
(0, '客户服务', 1, '/service', NULL, NULL, 'User', 2),
(0, '知识管理', 1, '/knowledge', NULL, NULL, 'Reading', 3),
(0, '工单管理', 1, '/workorder', NULL, NULL, 'Document', 4),
(0, '统计分析', 1, '/statistics', NULL, NULL, 'DataAnalysis', 5);

INSERT INTO `cs_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort_num`) VALUES
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '用户管理', 2, '/system/user', 'system/UserPage', 'system:user:list', 'UserFilled', 1),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '角色管理', 2, '/system/role', 'system/RolePage', 'system:role:list', 'Avatar', 2),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '菜单管理', 2, '/system/menu', 'system/MenuPage', 'system:menu:list', 'Menu', 3),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '操作日志', 2, '/system/log', 'system/LogPage', 'system:log:list', 'Notebook', 4),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '系统配置', 2, '/system/config', 'system/ConfigPage', 'system:config:list', 'Tools', 5),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '意图管理', 2, '/system/intent', 'system/IntentPage', 'system:intent:list', 'Target', 6),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '填槽配置', 2, '/system/slot', 'system/SlotPage', 'system:slot:list', 'Grid', 7),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '数据保留', 2, '/system/data-retention', 'system/DataRetentionPage', 'system:data-retention:list', 'Shield', 8),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='客户服务') t), 'AI聊天', 2, '/chat', 'ChatPage', 'chat:view', 'ChatDotRound', 1),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='客户服务') t), '客户管理', 2, '/customer', 'CustomerPage', 'customer:list', 'User', 2),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='客户服务') t), '会话管理', 2, '/service/session', 'service/SessionPage', 'service:session:list', 'ChatLineSquare', 3),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='知识管理') t), 'FAQ管理', 2, '/knowledge/faq', 'KnowledgePage', 'knowledge:faq:list', 'QuestionFilled', 1),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='知识管理') t), '文档管理', 2, '/knowledge/document', 'knowledge/DocumentPage', 'knowledge:document:list', 'Files', 2),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='知识管理') t), '知识图谱', 2, '/knowledge/graph', 'knowledge/GraphPage', 'knowledge:graph:list', 'Share', 3),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='知识管理') t), '多模态知识', 2, '/knowledge/multimodal', 'knowledge/MultimodalPage', 'knowledge:multimodal:list', 'PictureFilled', 4),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='工单管理') t), '工单列表', 2, '/workorder', 'WorkOrderPage', 'workorder:list', 'Tickets', 1),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='工单管理') t), '工单流程', 2, '/workorder/flow', 'workorder/FlowPage', 'workorder:flow:list', 'Connection', 2),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='统计分析') t), '数据概览', 2, '/statistics/overview', 'statistics/OverviewPage', 'statistics:overview', 'TrendCharts', 1),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='统计分析') t), '对话分析', 2, '/statistics/chat', 'statistics/ChatStatsPage', 'statistics:chat', 'ChatDotSquare', 2),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='统计分析') t), '工单分析', 2, '/statistics/workorder', 'statistics/WorkOrderStatsPage', 'statistics:workorder', 'DataBoard', 3);

-- 插入默认意图配置（兼容硬编码意图）
INSERT INTO `cs_intent_config` (`tenant_code`, `intent_code`, `intent_name`, `intent_type`, `description`, `keywords`, `examples`, `tool_bind`, `response_template`, `priority`, `enabled`, `del_flag`) VALUES
('default', 'CONSULT', '咨询', 'business', '政策、账户、使用方法等一般问题', '["咨询","问题","帮助","怎么","如何"]', '["如何重置密码","怎么联系客服","账户管理"]', NULL, NULL, 1, 1, 0),
('default', 'QUERY_LOGISTICS', '查物流', 'business', '询问进度、发货、物流（演示包；entity 填对方系统单号）', '["物流","快递","发货","进度","配送"]', '["查一下物流","快递到哪了","什么时候发货"]', 'query_logistics', NULL, 2, 1, 0),
('default', 'REFUND', '退款', 'business', '要求退款/退货（演示包；entity 填对方系统单号）', '["退款","退货","退钱","申请退款"]', '["我要退款","申请退货","怎么退款"]', 'apply_refund', NULL, 3, 1, 0),
('default', 'COMPLAINT', '投诉', 'business', '表达不满、催促处理，需要升级', '["投诉","不满","差评","催促"]', '["我要投诉","服务太差了","快点处理"]', NULL, NULL, 4, 1, 0),
('default', 'TO_AGENT', '转人工', 'transfer', '明确要求人工客服', '["转人工","人工客服","转接","客服"]', '["转人工客服","我要人工服务"]', NULL, NULL, 5, 1, 0);

-- 插入默认填槽配置（演示：查物流需要订单号）
INSERT INTO `cs_slot_filling` (`tenant_code`, `intent_code`, `slot_name`, `slot_type`, `required`, `prompt_template`, `validation_regex`, `extract_prompt`, `priority`, `enabled`, `del_flag`) VALUES
('default', 'QUERY_LOGISTICS', 'order_id', 'order_id', 1, '请提供您的订单号', '[A-Za-z0-9]{6,}', '从用户消息中提取订单号', 1, 1, 0),
('default', 'REFUND', 'order_id', 'order_id', 1, '请告诉我需要退款的订单编号', '[A-Za-z0-9]{6,}', '从用户消息中提取订单号', 1, 1, 0);

-- 插入默认数据保留策略
INSERT INTO `cs_data_retention` (`tenant_code`, `data_type`, `retention_days`, `allow_external_domain`, `allow_user_delete`, `anonymize_after_days`, `description`, `status`) VALUES
('default', 'session', 90, 0, 1, 180, '会话数据保留策略', 1),
('default', 'message', 90, 0, 1, 180, '消息数据保留策略', 1),
('default', 'knowledge', 365, 0, 0, NULL, '知识库数据保留策略', 1),
('default', 'tool_invoke', 180, 0, 0, 365, '工具调用审计保留策略', 1);

INSERT INTO `cs_user` (`username`, `password`, `real_name`, `email`, `status`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '超级管理员', 'admin@example.com', 1);

INSERT INTO `cs_user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `cs_user` u, `cs_role` r
WHERE u.username = 'admin' AND r.role_code = 'SUPER_ADMIN';

INSERT INTO `cs_role_menu` (`role_id`, `menu_id`)
SELECT r.id, m.id FROM `cs_role` r CROSS JOIN `cs_menu` m
WHERE r.role_code = 'SUPER_ADMIN';

SET FOREIGN_KEY_CHECKS = 1;

SELECT '数据库初始化完成' AS message;
