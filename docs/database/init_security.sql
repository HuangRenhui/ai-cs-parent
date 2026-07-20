-- ============================================
-- AI 智能客服系统 - 权限管理数据库初始化脚本
-- 包含: 用户表、角色表、菜单表、权限表
-- 创建时间: 2026-07-20
-- ============================================

USE ai_customer_service;

-- ============================================
-- 1. 系统用户表 (cs_user)
-- ============================================
DROP TABLE IF EXISTS `cs_user`;
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

-- ============================================
-- 2. 角色表 (cs_role)
-- ============================================
DROP TABLE IF EXISTS `cs_role`;
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

-- ============================================
-- 3. 菜单权限表 (cs_menu)
-- ============================================
DROP TABLE IF EXISTS `cs_menu`;
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

-- ============================================
-- 4. 用户角色关联表 (cs_user_role)
-- ============================================
DROP TABLE IF EXISTS `cs_user_role`;
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

-- ============================================
-- 5. 角色菜单关联表 (cs_role_menu)
-- ============================================
DROP TABLE IF EXISTS `cs_role_menu`;
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

-- ============================================
-- 6. 操作日志表 (cs_operation_log)
-- ============================================
DROP TABLE IF EXISTS `cs_operation_log`;
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

-- ============================================
-- 7. 数据统计表 (cs_statistics)
-- ============================================
DROP TABLE IF EXISTS `cs_statistics`;
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

-- ============================================
-- 8. 系统配置表 (cs_sys_config)
-- ============================================
DROP TABLE IF EXISTS `cs_sys_config`;
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

-- ============================================
-- 插入默认角色和菜单数据
-- ============================================

-- 插入默认角色
INSERT INTO `cs_role` (`role_name`, `role_code`, `description`, `sort_num`) VALUES
('超级管理员', 'SUPER_ADMIN', '系统超级管理员，拥有所有权限', 1),
('管理员', 'ADMIN', '系统管理员，拥有大部分管理权限', 2),
('坐席', 'AGENT', '客服坐席，拥有工单处理和客户管理权限', 3),
('普通用户', 'USER', '普通用户，仅拥有基本查看权限', 4);

-- 插入默认菜单
INSERT INTO `cs_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort_num`) VALUES
-- 一级目录
(0, '系统管理', 1, '/system', NULL, NULL, 'Setting', 1),
(0, '客户服务', 1, '/service', NULL, NULL, 'User', 2),
(0, '知识管理', 1, '/knowledge', NULL, NULL, 'Reading', 3),
(0, '工单管理', 1, '/workorder', NULL, NULL, 'Document', 4),
(0, '统计分析', 1, '/statistics', NULL, NULL, 'DataAnalysis', 5),
-- 系统管理子菜单
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '用户管理', 2, '/system/user', 'system/UserPage', 'system:user:list', 'UserFilled', 1),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '角色管理', 2, '/system/role', 'system/RolePage', 'system:role:list', 'Avatar', 2),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '菜单管理', 2, '/system/menu', 'system/MenuPage', 'system:menu:list', 'Menu', 3),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '操作日志', 2, '/system/log', 'system/LogPage', 'system:log:list', 'Notebook', 4),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='系统管理') t), '系统配置', 2, '/system/config', 'system/ConfigPage', 'system:config:list', 'Tools', 5),
-- 客户服务子菜单
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='客户服务') t), 'AI聊天', 2, '/chat', 'ChatPage', 'chat:view', 'ChatDotRound', 1),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='客户服务') t), '客户管理', 2, '/customer', 'CustomerPage', 'customer:list', 'User', 2),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='客户服务') t), '会话管理', 2, '/service/session', 'service/SessionPage', 'service:session:list', 'ChatLineSquare', 3),
-- 知识管理子菜单
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='知识管理') t), 'FAQ管理', 2, '/knowledge/faq', 'KnowledgePage', 'knowledge:faq:list', 'QuestionFilled', 1),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='知识管理') t), '文档管理', 2, '/knowledge/document', 'knowledge/DocumentPage', 'knowledge:document:list', 'Files', 2),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='知识管理') t), '知识图谱', 2, '/knowledge/graph', 'knowledge/GraphPage', 'knowledge:graph:list', 'Share', 3),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='知识管理') t), '多模态知识', 2, '/knowledge/multimodal', 'knowledge/MultimodalPage', 'knowledge:multimodal:list', 'PictureFilled', 4),
-- 工单管理子菜单
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='工单管理') t), '工单列表', 2, '/workorder', 'WorkOrderPage', 'workorder:list', 'Tickets', 1),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='工单管理') t), '工单流程', 2, '/workorder/flow', 'workorder/FlowPage', 'workorder:flow:list', 'Connection', 2),
-- 统计分析子菜单
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='统计分析') t), '数据概览', 2, '/statistics/overview', 'statistics/OverviewPage', 'statistics:overview', 'TrendCharts', 1),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='统计分析') t), '对话分析', 2, '/statistics/chat', 'statistics/ChatStatsPage', 'statistics:chat', 'ChatDotSquare', 2),
((SELECT id FROM (SELECT id FROM cs_menu WHERE menu_name='统计分析') t), '工单分析', 2, '/statistics/workorder', 'statistics/WorkOrderStatsPage', 'statistics:workorder', 'DataBoard', 3);

-- 为超级管理员分配所有角色
INSERT INTO `cs_user_role` (`user_id`, `role_id`)
SELECT 1, id FROM cs_role WHERE role_code = 'SUPER_ADMIN';

-- 为超级管理员角色分配所有菜单
INSERT INTO `cs_role_menu` (`role_id`, `menu_id`)
SELECT (SELECT id FROM cs_role WHERE role_code = 'SUPER_ADMIN'), id FROM cs_menu;

-- 插入默认超级管理员用户 (密码: admin123)
INSERT INTO `cs_user` (`username`, `password`, `real_name`, `email`, `status`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '超级管理员', 'admin@example.com', 1);

-- ============================================
-- 完成
-- ============================================
SELECT '权限管理系统表初始化完成！' AS message;
