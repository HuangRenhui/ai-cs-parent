-- ============================================
-- AI 智能客服系统 - 数据库初始化脚本
-- 数据库名称: ai_customer_service
-- 创建时间: 2026-06-20
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS ai_customer_service 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_unicode_ci;

USE ai_customer_service;

-- ============================================
-- 1. 客户表 (cs_customer)
-- ============================================
DROP TABLE IF EXISTS `cs_customer`;
CREATE TABLE `cs_customer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '客户ID',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `nickname` VARCHAR(100) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `customer_tag` VARCHAR(200) DEFAULT NULL COMMENT '客户标签',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_phone` (`phone`),
    KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户信息表';

-- ============================================
-- 2. 坐席表 (cs_agent)
-- ============================================
DROP TABLE IF EXISTS `cs_agent`;
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

-- ============================================
-- 3. 聊天会话表 (cs_chat_session)
-- ============================================
DROP TABLE IF EXISTS `cs_chat_session`;
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

-- ============================================
-- 4. 聊天消息表 (cs_chat_msg)
-- ============================================
DROP TABLE IF EXISTS `cs_chat_msg`;
CREATE TABLE `cs_chat_msg` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `msg_content` TEXT NOT NULL COMMENT '消息内容',
    `msg_type` TINYINT DEFAULT 1 COMMENT '消息类型: 1-文本, 2-图片, 3-文件, 4-表情',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- ============================================
-- 5. 知识库FAQ表 (cs_knowledge_faq)
-- ============================================
DROP TABLE IF EXISTS `cs_knowledge_faq`;
CREATE TABLE `cs_knowledge_faq` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'FAQ ID',
    `question` VARCHAR(500) NOT NULL COMMENT '问题',
    `answer` TEXT NOT NULL COMMENT '答案',
    `category` VARCHAR(100) DEFAULT NULL COMMENT '分类',
    `sort_num` INT DEFAULT 0 COMMENT '排序号',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `milvus_id` VARCHAR(100) DEFAULT NULL COMMENT 'Milvus向量数据库中的记录ID',
    `del_flag` TINYINT DEFAULT 0 COMMENT '删除标记: 0-未删除, 1-已删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`),
    KEY `idx_milvus_id` (`milvus_id`),
    KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库FAQ表';

-- ============================================
-- 6. 工单表 (cs_work_order)
-- ============================================
DROP TABLE IF EXISTS `cs_work_order`;
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

-- ============================================
-- 插入测试数据（可选）
-- ============================================

-- 插入测试坐席
INSERT INTO `cs_agent` (`agent_account`, `agent_pwd`, `agent_name`, `agent_status`) VALUES
('admin', '123456', '管理员', 1),
('agent001', '123456', '客服小张', 1),
('agent002', '123456', '客服小李', 0);

-- 插入测试客户
INSERT INTO `cs_customer` (`phone`, `nickname`, `customer_tag`) VALUES
('13800138001', '测试用户1', 'VIP'),
('13800138002', '测试用户2', '普通'),
('13800138003', '测试用户3', '新用户');

-- 插入测试FAQ
INSERT INTO `cs_knowledge_faq` (`question`, `answer`, `category`, `sort_num`, `status`) VALUES
('如何重置密码？', '您可以在登录页面点击"忘记密码"，然后通过手机号验证码重置密码。', '账户管理', 1, 1),
('如何联系客服？', '您可以通过在线客服、电话400-xxx-xxxx或邮件support@example.com联系我们。', '联系方式', 2, 1),
('退款政策是什么？', '我们支持7天无理由退款，请在订单详情页申请退款。', '售后服务', 3, 1),
('如何修改个人信息？', '登录后进入"个人中心"-"账户设置"即可修改个人信息。', '账户管理', 4, 1),
('支持哪些支付方式？', '我们支持支付宝、微信支付、银行卡等多种支付方式。', '支付问题', 5, 1);

-- ============================================
-- 完成
-- ============================================
SELECT '数据库初始化完成！' AS message;
