-- 为 cs_knowledge_faq 表添加 milvus_id 字段
-- 用于存储 Milvus 向量数据库中的记录ID，实现增量更新

ALTER TABLE cs_knowledge_faq 
ADD COLUMN milvus_id VARCHAR(100) DEFAULT NULL COMMENT 'Milvus向量数据库中的记录ID';

-- 添加索引以提高查询性能
CREATE INDEX idx_milvus_id ON cs_knowledge_faq(milvus_id);
