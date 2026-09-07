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
  enabled TINYINT DEFAULT 1 COMMENT '是否启用: 1是0否',
  is_active TINYINT DEFAULT 0 COMMENT '当前生效(同能力仅一条): 1是0否',
  health VARCHAR(16) DEFAULT 'UNKNOWN' COMMENT 'UNKNOWN/HEALTHY/DOWN',
  remark VARCHAR(500) COMMENT '备注',
  create_time DATETIME,
  update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型注册表';
-- 唯一约束: 同一能力下仅一个 active 由程序保证
