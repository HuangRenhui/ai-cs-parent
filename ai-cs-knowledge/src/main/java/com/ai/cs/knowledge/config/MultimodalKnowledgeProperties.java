package com.ai.cs.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多模态知识库配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "multimodal-knowledge")
public class MultimodalKnowledgeProperties {

    /**
     * 是否启用多模态知识库
     */
    private boolean enabled = true;

    /**
     * 多模态大模型配置
     */
    private VisionModel visionModel = new VisionModel();

    /**
     * 视频知识库配置
     */
    private Video video = new Video();

    /**
     * 混合检索配置
     */
    private HybridSearch hybridSearch = new HybridSearch();

    @Data
    public static class VisionModel {
        /**
         * 多模态大模型类型: ollama, openai, local
         */
        private String type = "ollama";

        /**
         * 模型名称
         */
        private String modelName = "llava:latest";

        /**
         * API地址
         */
        private String baseUrl = "http://localhost:11434";

        /**
         * API密钥
         */
        private String apiKey = "";

        /**
         * 最大Token数
         */
        private int maxTokens = 2048;

        /**
         * 温度参数
         */
        private double temperature = 0.1;
    }

    @Data
    public static class Video {
        /**
         * 视频帧提取间隔（秒）
         */
        private int frameInterval = 5;

        /**
         * 每次最多提取帧数
         */
        private int maxFrames = 20;

        /**
         * 帧图片存储路径
         */
        private String frameStoragePath = "./uploads/video-frames";

        /**
         * 视频摘要最大长度
         */
        private int maxSummaryLength = 500;

        /**
         * 视频分析模型
         */
        private String analysisModel = "llava:latest";
    }

    @Data
    public static class HybridSearch {
        /**
         * 文本检索权重
         */
        private double textWeight = 1.0;

        /**
         * 图片检索权重
         */
        private double imageWeight = 0.8;

        /**
         * 音频检索权重
         */
        private double audioWeight = 0.6;

        /**
         * 视频检索权重
         */
        private double videoWeight = 0.5;

        /**
         * 混合检索融合方法: rrf, weighted, linear
         */
        private String fusionMethod = "rrf";

        /**
         * RRF参数k（Reciprocal Rank Fusion）
         */
        private int rrfK = 60;
    }
}
