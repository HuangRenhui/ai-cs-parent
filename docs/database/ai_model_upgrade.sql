-- ============================================
-- 模型注册表增强：超时 / 熔断阈值 / 单价 / 用量记录
-- 适用于已有 cs_ai_model 的库；新库直接执行 ai_model.sql
-- ============================================

ALTER TABLE `cs_ai_model`
  ADD COLUMN `timeout_ms` INT DEFAULT 20000 COMMENT '单次调用超时(毫秒)' AFTER `priority`,
  ADD COLUMN `fail_threshold` INT DEFAULT 2 COMMENT '连续失败熔断阈值(达到即摘除)' AFTER `timeout_ms`,
  ADD COLUMN `cost_per_1k_in` DECIMAL(10,6) DEFAULT NULL COMMENT '输入单价(元/千token)' AFTER `fail_threshold`,
  ADD COLUMN `cost_per_1k_out` DECIMAL(10,6) DEFAULT NULL COMMENT '输出单价(元/千token)' AFTER `cost_per_1k_in`;

-- ============================================
-- 模型调用用量记录 (cs_model_usage)
-- 由 ai-cs-job 从 Redis 流消费落库，供用量统计与告警
-- ============================================
CREATE TABLE IF NOT EXISTS `cs_model_usage` (
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
  `success` TINYINT DEFAULT 1 COMMENT '是否成功: 1是0否',
  `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  KEY `idx_model` (`model_id`),
  KEY `idx_type_time` (`model_type`, `create_time`),
  KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型调用用量记录';
