package com.ai.cs.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识图谱配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge-graph")
public class KnowledgeGraphProperties {

    /**
     * 是否启用知识图谱
     */
    private boolean enabled = false;

    /**
     * 图数据库类型: neo4j, nebula
     */
    private String databaseType = "neo4j";

    /**
     * Neo4j 连接配置
     */
    private Neo4j neo4j = new Neo4j();

    /**
     * NebulaGraph 连接配置
     */
    private Nebula nebula = new Nebula();

    /**
     * 图谱构建配置
     */
    private Build build = new Build();

    /**
     * 实体关系抽取配置
     */
    private Extraction extraction = new Extraction();

    @Data
    public static class Neo4j {
        private String uri = "bolt://localhost:7687";
        private String username = "neo4j";
        private String password = "password";
        private String database = "neo4j";
    }

    @Data
    public static class Nebula {
        private String address = "localhost:9669";
        private String username = "root";
        private String password = "nebula";
        private String space = "knowledge_graph";
    }

    @Data
    public static class Build {
        /**
         * 是否自动从文档构建图谱
         */
        private boolean autoBuild = false;

        /**
         * 最大实体数量
         */
        private int maxEntities = 10000;

        /**
         * 最大关系数量
         */
        private int maxRelations = 50000;

        /**
         * 实体名称最小置信度
         */
        private double minConfidence = 0.6;
    }

    @Data
    public static class Extraction {
        /**
         * 抽取方式: rule, llm, hybrid
         */
        private String method = "hybrid";

        /**
         * LLM抽取使用的模型
         */
        private String llmModel = "qwen:7b";

        /**
         * 每次处理的最大文本长度
         */
        private int maxTextLength = 2000;

        /**
         * 关系类型: 包含、依赖、属于、等同于、引用
         */
        private String relationTypes = "包含,依赖,属于,等同于,引用,相关,前置,后置";
    }
}
