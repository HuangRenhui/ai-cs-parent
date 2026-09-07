-- ============================================
-- 会话入口场景配置 (cs_scene_config)
-- 场景化入口：点订单/产品/售后进入客服时，按场景下发开场白与快捷动作
-- 已有库直接执行本脚本；新库由 init.sql 全量创建
-- ============================================
CREATE TABLE IF NOT EXISTS `cs_scene_config` (
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

INSERT INTO `cs_scene_config` (`scene`, `scene_name`, `greeting`, `greeting_empty`, `quick_actions`, `enabled`, `sort_num`)
SELECT * FROM (SELECT
    'ORDER' AS scene, '订单入口' AS scene_name,
    '您好，看到您正在咨询订单 {entityId}。您可以点击下方按钮快速操作，或直接描述问题。' AS greeting,
    '您好，您正在咨询订单相关服务。请提供订单号，或点击下方按钮。' AS greeting_empty,
    '[{"label":"查看订单情况","send":"帮我查一下这个订单的情况"},{"label":"查询物流","send":"查询物流进度"},{"label":"申请退款","send":"我要申请退款"},{"label":"转人工","send":"转人工"}]' AS quick_actions,
    1 AS enabled, 1 AS sort_num
UNION ALL SELECT
    'PRODUCT', '产品入口',
    '您好，您正在了解产品 {entityId}。我可以为您介绍详细信息、价格与优惠，也可以直接回答您的疑问。',
    '您好，欢迎咨询我们的产品。请告诉我您想了解哪款产品，或直接提问。',
    '[{"label":"了解产品信息","send":"我想了解这个产品的详细信息"},{"label":"价格与优惠","send":"这个产品的价格和优惠活动"},{"label":"转人工","send":"转人工"}]',
    1, 2
UNION ALL SELECT
    'AFTER_SALE', '售后入口',
    '您好，这里是售后通道。若需对订单 {entityId} 申请退款/售后，请点击下方按钮或描述问题。',
    '您好，这里是售后通道。请提供需要售后的订单号，或点击下方按钮。',
    '[{"label":"申请退款","send":"我要申请退款"},{"label":"查询售后进度","send":"查询售后处理进度"},{"label":"转人工","send":"转人工"}]',
    1, 3
UNION ALL SELECT
    'GENERAL', '通用入口',
    '您好，我是智能客服。可咨询问题；物流/退款会走已注册的连接器，而不是内核订单表。',
    NULL, '[]', 1, 99) seed
WHERE NOT EXISTS (SELECT 1 FROM `cs_scene_config` LIMIT 1);
