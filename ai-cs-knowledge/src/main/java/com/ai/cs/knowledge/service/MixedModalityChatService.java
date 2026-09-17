package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 图文混合对话增强服务（占位）
 *
 * <p>TODO 后续实现：多模态输入（文字 + 图片 + 音频 + 视频）经 {@code VisionLLMClient}
 * 理解后拼接上下文，结合知识库混合检索（{@code HybridRetrievalService}）与提示词模板
 * （{@code PromptTemplateService}）组装 Prompt，调用大模型生成文本/富媒体回答，
 * 并按知识来源分数计算置信度。</p>
 *
 * <p>当前不调用视觉模型、不做检索：{@link #multimodalChat} 返回「未实现」响应结构，
 * 声明了 {@code throws IOException} 的三个入口抛 {@link IOException}，
 * {@link #generateRichResponse} 返回空 Map。两个请求/响应承载类型为结果载体。</p>
 */
@Slf4j
@Service
public class MixedModalityChatService {

    private final VisionLLMClient visionLLMClient;
    private final MultimodalKnowledgeService knowledgeService;
    private final HybridRetrievalService hybridRetrievalService;
    private final PromptTemplateService promptTemplateService;

    public MixedModalityChatService(VisionLLMClient visionLLMClient,
                                     MultimodalKnowledgeService knowledgeService,
                                     HybridRetrievalService hybridRetrievalService,
                                     PromptTemplateService promptTemplateService) {
        this.visionLLMClient = visionLLMClient;
        this.knowledgeService = knowledgeService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.promptTemplateService = promptTemplateService;
    }

    /** 未实现时统一的回答文案 */
    private static final String NOT_IMPLEMENTED = "图文混合对话为占位实现，后端未接入多模态大模型与知识库检索";

    /**
     * 多模态对话请求
     */
    public static class MultimodalChatRequest {
        public String textInput;                // 文本输入
        public List<String> imagePaths;         // 图片路径列表
        public String audioPath;                // 音频路径
        public String videoPath;                // 视频路径
        public boolean includeKnowledgeBase;    // 是否包含知识库检索
        public Map<String, Object> context;     // 额外上下文
    }

    /**
     * 多模态对话响应
     */
    public static class MultimodalChatResponse {
        public String textAnswer;               // 文本回答
        public List<String> referencedImages;   // 引用的图片
        public List<String> referencedAudios;   // 引用的音频
        public List<String> referencedDocs;     // 引用的文档
        public List<Map<String, Object>> knowledgeSources; // 知识来源
        public String responseType;             // TEXT, RICH, MULTIMODAL
        public double confidence;
    }

    /**
     * 图文混合对话（占位：返回未实现响应）
     *
     * @return 仅含未实现文案的响应（responseType=TEXT、confidence=0）
     */
    public MultimodalChatResponse multimodalChat(MultimodalChatRequest request) {
        log.warn("[占位] 图文混合对话未实现");

        MultimodalChatResponse response = new MultimodalChatResponse();
        response.textAnswer = NOT_IMPLEMENTED;
        response.responseType = "TEXT";
        response.confidence = 0;
        response.referencedImages = List.of();
        response.referencedAudios = List.of();
        response.referencedDocs = List.of();
        response.knowledgeSources = List.of();
        return response;
    }

    /**
     * 图片驱动对话（占位：抛 IOException）
     *
     * @throws IOException 占位实现
     */
    public MultimodalChatResponse imageDrivenChat(String imagePath, String question) throws IOException {
        log.warn("[占位] 图片驱动对话未实现 imagePath={}", imagePath);
        throw new IOException("图片驱动对话为占位实现，后端未接入视觉模型");
    }

    /**
     * 多图比较对话（占位：抛 IOException）
     *
     * @throws IOException 占位实现
     */
    public String compareImages(List<String> imagePaths, String comparisonAspect) throws IOException {
        log.warn("[占位] 多图比较未实现 count={}", imagePaths == null ? 0 : imagePaths.size());
        throw new IOException("多图比较为占位实现，后端未接入视觉模型");
    }

    /**
     * 音视频联合分析（占位：抛 IOException）
     *
     * @throws IOException 占位实现
     */
    public String analyzeAudioVideo(String audioPath, String videoPath, String question) throws IOException {
        log.warn("[占位] 音视频联合分析未实现");
        throw new IOException("音视频联合分析为占位实现，后端未接入音视频理解模型");
    }

    /**
     * 多模态输出生成（占位：返回空 Map）
     */
    public Map<String, Object> generateRichResponse(String question, boolean includeImages,
                                                      boolean includeCharts, boolean includeLinks) {
        log.info("[占位] 多模态富响应生成未实现");
        return Map.of();
    }
}
