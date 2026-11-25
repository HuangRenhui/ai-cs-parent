-- ============================================================
-- 迁移：2026-11-25 多租户隔离（坐席账号 + 工具调用幂等键）
-- 适用：已用旧版 init.sql 初始化的环境；全新建库直接用 init.sql，无需执行本脚本
-- 内容：
--   一、cs_agent：新增 tenant_code，唯一键收敛到 (tenant_code, agent_account)
--   二、cs_open_tool_invoke：幂等唯一键改为 (tenant_code, idempotency_key)
-- 执行前：请先备份两张表
--   mysqldump -t <db> cs_agent cs_open_tool_invoke > tenant_isolation.bak.sql
-- 幂等：可重复执行，已存在的列/索引会自动跳过
-- ============================================================

SET @db := DATABASE();

-- ============================================================
-- 一、cs_agent 支持多租户隔离
-- 目标：坐席账号唯一性收敛到租户内，登录与坐席管理均按 tenant_code 过滤
-- ============================================================

-- 1) 新增租户列（NOT NULL DEFAULT 'default'，存量行自动落到 default）
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'cs_agent' AND COLUMN_NAME = 'tenant_code') = 0,
    'ALTER TABLE `cs_agent` ADD COLUMN `tenant_code` VARCHAR(64) NOT NULL DEFAULT ''default'' COMMENT ''租户编码'' AFTER `id`',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 回填历史空值（DEFAULT 已覆盖，这里兜住曾被显式写成空串的行）
UPDATE `cs_agent` SET `tenant_code` = 'default'
 WHERE `tenant_code` IS NULL OR `tenant_code` = '';

-- 3) 唯一键改为租户维度：先加新键，再删旧键，避免中途失去唯一约束
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'cs_agent' AND INDEX_NAME = 'uk_tenant_agent_account') = 0,
    'ALTER TABLE `cs_agent` ADD UNIQUE KEY `uk_tenant_agent_account` (`tenant_code`, `agent_account`)',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'cs_agent' AND INDEX_NAME = 'uk_agent_account') > 0,
    'ALTER TABLE `cs_agent` DROP INDEX `uk_agent_account`',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 租户过滤索引
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'cs_agent' AND INDEX_NAME = 'idx_tenant_code') = 0,
    'ALTER TABLE `cs_agent` ADD KEY `idx_tenant_code` (`tenant_code`)',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) 存量数据归属：默认全部归入 default。
--    若旧库里坐席实际分属多个租户，需按台账人工修正，例如：
--    UPDATE `cs_agent` SET `tenant_code` = 'tenantA' WHERE `agent_account` IN ('agent001', 'agent002');

-- 6) 校验：同一租户内不应出现重复账号
-- SELECT `tenant_code`, `agent_account`, COUNT(*) AS cnt
--   FROM `cs_agent` GROUP BY `tenant_code`, `agent_account` HAVING cnt > 1;

-- ============================================================
-- 二、工具调用幂等键按租户隔离
-- 背景：幂等键由调用方自定义，旧唯一键 uk_open_invoke_idem(idempotency_key) 是全局的，
--       两个租户使用相同的键（如 "1" / "refund-001"）会互相命中并重放对方的业务结果。
-- 目标：唯一键改为 (tenant_code, idempotency_key)，查询与重放均带租户条件。
-- ============================================================

-- 1) 先加租户维度唯一键（旧键更严格，先加不会冲突）
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'cs_open_tool_invoke'
        AND INDEX_NAME = 'uk_open_invoke_tenant_idem') = 0,
    'ALTER TABLE `cs_open_tool_invoke` ADD UNIQUE KEY `uk_open_invoke_tenant_idem` (`tenant_code`, `idempotency_key`)',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 删除旧的全局唯一键
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'cs_open_tool_invoke'
        AND INDEX_NAME = 'uk_open_invoke_idem') > 0,
    'ALTER TABLE `cs_open_tool_invoke` DROP INDEX `uk_open_invoke_idem`',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 存量脏数据检查：同租户内不应出现重复幂等键
-- SELECT `tenant_code`, `idempotency_key`, COUNT(*) AS cnt
--   FROM `cs_open_tool_invoke`
--  WHERE `idempotency_key` IS NOT NULL
--  GROUP BY `tenant_code`, `idempotency_key` HAVING cnt > 1;
