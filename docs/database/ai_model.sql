-- ============================================
-- AI 模型注册与管理表 (cs_ai_model)
-- 支持本地(Ollama)与在线(DashScope/OpenAI兼容/DeepSeek)
-- ============================================
DROP TABLE IF EXISTS cs_ai_model;
CREATE TABLE cs_ai_model (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  model_name VARCHAR(100) NOT NULL COMMENT '显示名称',
  provider VARCHAR(32) NOT NULL COMMENT '供应方: ollama/dashscope/openai/deepseek/other',
  model_type VARCHAR(32) NOT NULL COMMENT '能力: LLM/EMBEDDING/RERANK/VISION/MULTIMODAL',
  base_url VARCHAR(500) COMMENT '服务地址',
  api_key VARCHAR(1000) COMMENT '密钥(AES-GCM加密存储 enc: 前缀)',
  api_secret VARCHAR(1000) COMMENT '附加密钥(AES-GCM加密存储)',
  remote_model VARCHAR(100) COMMENT '上游模型标识',
  temperature DECIMAL(4,2) DEFAULT 0.30 COMMENT '温度',
  dimension INT DEFAULT 1024 COMMENT 'embedding维度',
  priority INT DEFAULT 0 COMMENT '故障切换优先级(越小越优先)',
  timeout_ms INT DEFAULT 20000 COMMENT '单次调用超时(毫秒)',
  fail_threshold INT DEFAULT 2 COMMENT '连续失败熔断阈值(达到即摘除)',
  cost_per_1k_in DECIMAL(10,6) DEFAULT NULL COMMENT '输入单价(元/千token)',
  cost_per_1k_out DECIMAL(10,6) DEFAULT NULL COMMENT '输出单价(元/千token)',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用: 1是0否',
  is_active TINYINT DEFAULT 0 COMMENT '当前生效(同能力仅一条): 1是0否',
  health VARCHAR(16) DEFAULT 'UNKNOWN' COMMENT 'UNKNOWN/HEALTHY/DOWN',
  remark VARCHAR(500) COMMENT '备注',
  create_time DATETIME,
  update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型注册表';
-- 唯一约束: 同一能力下仅一个 active 由程序保证

-- ============================================
-- 模型调用用量记录 (cs_model_usage)
-- 由 ai-cs-job 从 Redis 流消费落库，供用量统计与告警
-- ============================================
DROP TABLE IF EXISTS cs_model_usage;
CREATE TABLE cs_model_usage (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  model_id BIGINT COMMENT '模型ID',
  model_name VARCHAR(100) COMMENT '模型名称',
  model_type VARCHAR(32) COMMENT '能力类型',
  provider VARCHAR(32) COMMENT '供应方',
  session_id VARCHAR(64) COMMENT '会话ID',
  prompt_tokens INT DEFAULT 0 COMMENT '输入token',
  completion_tokens INT DEFAULT 0 COMMENT '输出token',
  total_tokens INT DEFAULT 0 COMMENT '总token',
  latency_ms INT DEFAULT 0 COMMENT '耗时(毫秒)',
  success TINYINT DEFAULT 1 COMMENT '是否成功: 1是0否',
  error_msg VARCHAR(500) COMMENT '失败原因',
  create_time DATETIME,
  KEY idx_model (model_id),
  KEY idx_type_time (model_type, create_time),
  KEY idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型调用用量记录';
