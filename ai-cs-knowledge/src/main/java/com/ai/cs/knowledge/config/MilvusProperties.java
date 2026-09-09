package com.ai.cs.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Milvus向量数据库配置属性类
 * 从application.yml读取milvus前缀的配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {
    /** Milvus服务地址 */
    private String host = "localhost";
    /** Milvus服务端口 */
    private Integer port = 19530;
    /** 向量集合（Collection）名称 */
    private String collectionName = "cs_kb_faq";
    /** IVF索引建库时的聚类簇数，影响索引精度与构建速度 */
    private int nlist = 128;
    /** 查询时探测的簇数，越大召回越准但越慢 */
    private int nprobe = 16;
    /** 向量检索默认返回的Top结果数 */
    private int topK = 5;
    /** COSINE 分数下限，低于则视为未命中 */
    private float scoreThreshold = 0.40f;
    /** 存入集合的单条文本内容最大长度（字符），超长需截断 */
    private int contentMaxLength = 8192;
    /** 向量维度，必须与所用Embedding模型输出维度一致 */
    private int dimension = 1024;
}
