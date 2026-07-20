-- =============================================
-- 知识库增强功能数据库表结构
-- 创建时间: 2026-07-20
-- =============================================

-- 1. 知识图谱节点表
CREATE TABLE IF NOT EXISTS `cs_knowledge_graph_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `node_id` VARCHAR(64) NOT NULL COMMENT '节点唯一标识',
    `name` VARCHAR(255) NOT NULL COMMENT '节点名称（实体名称）',
    `label` VARCHAR(100) NOT NULL DEFAULT '概念' COMMENT '节点标签/类型',
    `properties` TEXT COMMENT '节点属性（JSON格式）',
    `description` TEXT COMMENT '节点描述',
    `source_document_id` VARCHAR(128) COMMENT '来源文档ID',
    `source_document_name` VARCHAR(255) COMMENT '来源文档名称',
    `importance` INT DEFAULT 50 COMMENT '重要性评分（0-100）',
    `is_core` TINYINT DEFAULT 0 COMMENT '是否为核心节点 0-否 1-是',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0-草稿 1-已发布 2-已归档',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT DEFAULT 0 COMMENT '逻辑删除 0-正常 1-删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_node_id` (`node_id`),
    KEY `idx_name` (`name`),
    KEY `idx_label` (`label`),
    KEY `idx_source_doc` (`source_document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱节点表';

-- 2. 知识图谱关系表
CREATE TABLE IF NOT EXISTS `cs_knowledge_graph_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `relation_id` VARCHAR(64) NOT NULL COMMENT '关系唯一标识',
    `source_node_id` VARCHAR(64) NOT NULL COMMENT '源节点ID',
    `target_node_id` VARCHAR(64) NOT NULL COMMENT '目标节点ID',
    `source_node_name` VARCHAR(255) COMMENT '源节点名称',
    `target_node_name` VARCHAR(255) COMMENT '目标节点名称',
    `relation_type` VARCHAR(50) NOT NULL COMMENT '关系类型',
    `description` TEXT COMMENT '关系描述',
    `properties` TEXT COMMENT '关系属性（JSON格式）',
    `weight` INT DEFAULT 50 COMMENT '关系权重（0-100）',
    `confidence` DOUBLE DEFAULT 0.7 COMMENT '关系置信度（0.0-1.0）',
    `source_document_id` VARCHAR(128) COMMENT '来源文档ID',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0-草稿 1-已发布 2-已归档',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT DEFAULT 0 COMMENT '逻辑删除 0-正常 1-删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_relation_id` (`relation_id`),
    KEY `idx_source_node` (`source_node_id`),
    KEY `idx_target_node` (`target_node_id`),
    KEY `idx_relation_type` (`relation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱关系表';

-- 3. 多模态知识条目表
CREATE TABLE IF NOT EXISTS `cs_multimodal_knowledge` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `knowledge_id` VARCHAR(64) NOT NULL COMMENT '知识条目唯一标识',
    `modality` VARCHAR(20) NOT NULL COMMENT '模态类型: IMAGE/AUDIO/VIDEO/MIXED',
    `resource_path` VARCHAR(500) COMMENT '资源文件路径',
    `resource_id` VARCHAR(128) COMMENT '资源文件ID',
    `title` VARCHAR(255) NOT NULL COMMENT '知识标题',
    `description` TEXT COMMENT '知识内容描述',
    `analysis` TEXT COMMENT 'AI生成的详细分析文本',
    `keywords` VARCHAR(500) COMMENT '关键词（JSON数组）',
    `entities` TEXT COMMENT '提取的实体（JSON数组）',
    `tags` VARCHAR(500) COMMENT '标签列表（逗号分隔）',
    `source_document_id` VARCHAR(128) COMMENT '来源文档ID',
    `vector_id` VARCHAR(128) COMMENT '向量ID',
    `vector_collection` VARCHAR(64) COMMENT '向量集合名称',
    `confidence` DOUBLE DEFAULT 0.8 COMMENT '置信度（0.0-1.0）',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0-处理中 1-已入库 2-已归档',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT DEFAULT 0 COMMENT '逻辑删除 0-正常 1-删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_knowledge_id` (`knowledge_id`),
    KEY `idx_modality` (`modality`),
    KEY `idx_resource_id` (`resource_id`),
    KEY `idx_tags` (`tags`(191)),
    KEY `idx_vector_id` (`vector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多模态知识条目表';
