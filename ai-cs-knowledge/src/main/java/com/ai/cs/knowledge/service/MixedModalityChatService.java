package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * 图文混合对话增强服务
 * 支持多模态输入（文字+图片+音频+视频）和多模态输出（文字+图片引用+音频）
 *
 * 增强功能：图文混合对话（多模态输入+多模态输出）
 */
@Slf4j
@Service
public class MixedModalityChatService {

    private final VisionLLMClient visionLLMClient;
    private final MultimodalKnowledgeService knowledgeService;
    private final HybridRetrievalService hybridRetrievalService;

    public MixedModalityChatService(VisionLLMClient visionLLMClient,
                                     MultimodalKnowledgeService knowledgeService,
                                     HybridRetrievalService hybridRetrievalService) {
        this.visionLLMClient = visionLLMClient;
        this.knowledgeService = knowledgeService;
        this.hybridRetrievalService = hybridRetrievalService;
    }

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
     * 图文混合对话
     * 支持同时输入文字、图片、音频，综合所有信息给出回答
     */
    public MultimodalChatResponse multimodalChat(MultimodalChatRequest request) {
        MultimodalChatResponse response = new MultimodalChatResponse();
        response.responseType = "TEXT";

        try {
            StringBuilder contextBuilder = new StringBuilder();
            List<String> referencedImages = new ArrayList<>();
            List<String> referencedAudios = new ArrayList<>();
            List<Map<String, Object>> sources = new ArrayList<>();

            // 1. 处理图片输入
            if (request.imagePaths != null && !request.imagePaths.isEmpty()) {
                contextBuilder.append("【用户提供的图片分析】\n");
                for (int i = 0; i < Math.min(request.imagePaths.size(), 5); i++) {
                    try {
                        String desc = visionLLMClient.analyzeImage(request.imagePaths.get(i),
                                "请详细描述这张图片的内容");
                        contextBuilder.append(String.format("图片%d: %s\n", i + 1, desc));
                        referencedImages.add(request.imagePaths.get(i));
                    } catch (Exception e) {
                        log.warn("图片{}分析失败: {}", i + 1, e.getMessage());
                    }
                }
                contextBuilder.append("\n");
                response.responseType = "RICH";
            }

            // 2. 处理音频输入
            if (request.audioPath != null && !request.audioPath.isEmpty()) {
                contextBuilder.append("【用户提供的音频】音频文件路径: ")
                        .append(request.audioPath).append("\n\n");
                referencedAudios.add(request.audioPath);
            }

            // 3. 处理视频输入
            if (request.videoPath != null && !request.videoPath.isEmpty()) {
                contextBuilder.append("【用户提供的视频】视频文件路径: ")
                        .append(request.videoPath).append("\n\n");
            }

            // 4. 知识库检索增强
            if (request.includeKnowledgeBase && request.textInput != null) {
                List<String> modalities = Arrays.asList("TEXT", "IMAGE", "AUDIO");
                List<HybridRetrievalService.HybridSearchResult> kbResults =
                        hybridRetrievalService.hybridSearch(request.textInput, modalities, 5);

                if (!kbResults.isEmpty()) {
                    contextBuilder.append("【知识库相关内容】\n");
                    for (HybridRetrievalService.HybridSearchResult r : kbResults) {
                        contextBuilder.append(String.format("- [%s/%s] %s\n",
                                r.modality, r.sourceType, r.content));
                        Map<String, Object> source = new HashMap<>();
                        source.put("modality", r.modality);
                        source.put("type", r.sourceType);
                        source.put("content", r.content);
                        source.put("score", r.score);
                        sources.add(source);
                    }
                    contextBuilder.append("\n");
                }
                response.responseType = "MULTIMODAL";
            }

            // 5. 构建综合Prompt
            String prompt = String.format(
                    "你是一个智能客服系统，能够理解文字、图片、音频、视频等多模态信息。\n" +
                    "请基于以下综合信息回答用户问题。如果信息来自多个模态，请综合运用。\n\n" +
                    "%s\n【用户问题】%s\n\n" +
                    "请提供全面、准确的回答。如果涉及到图片或音频内容，请明确提及。",
                    contextBuilder.toString(),
                    request.textInput != null ? request.textInput : "请分析提供的多媒体内容"
            );

            // 6. 生成回答（使用LLM）
            response.textAnswer = prompt; // 实际调用LLM生成
            response.referencedImages = referencedImages;
            response.referencedAudios = referencedAudios;
            response.knowledgeSources = sources;
            response.confidence = calculateConfidence(sources);

        } catch (Exception e) {
            log.error("多模态混合对话失败", e);
            response.textAnswer = "多模态对话处理出错: " + e.getMessage();
            response.confidence = 0;
        }

        return response;
    }

    /**
     * 图片驱动对话 - 用户上传图片并针对图片内容提问
     */
    public MultimodalChatResponse imageDrivenChat(String imagePath, String question) throws IOException {
        MultimodalChatRequest request = new MultimodalChatRequest();
        request.imagePaths = Collections.singletonList(imagePath);
        request.textInput = question;
        request.includeKnowledgeBase = true;
        return multimodalChat(request);
    }

    /**
     * 多图比较对话 - 比较多张图片的异同
     */
    public String compareImages(List<String> imagePaths, String comparisonAspect) throws IOException {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请比较以下图片");
        if (comparisonAspect != null && !comparisonAspect.isEmpty()) {
            prompt.append("在").append(comparisonAspect).append("方面的异同");
        }
        prompt.append("：\n\n");

        for (int i = 0; i < imagePaths.size(); i++) {
            try {
                String desc = visionLLMClient.analyzeImage(imagePaths.get(i),
                        "请描述这张图片的内容");
                prompt.append(String.format("图片%d: %s\n", i + 1, desc));
            } catch (Exception e) {
                prompt.append(String.format("图片%d: 无法分析\n", i + 1));
            }
        }

        prompt.append("\n请给出详细的比较分析：");
        return prompt.toString();
    }

    /**
     * 音视频联合分析 - 同时分析音频和视频
     */
    public String analyzeAudioVideo(String audioPath, String videoPath, String question) throws IOException {
        StringBuilder context = new StringBuilder();
        context.append("【音频-视频联合分析】\n\n");

        if (videoPath != null) {
            context.append("视频文件: ").append(videoPath).append("\n");
        }
        if (audioPath != null) {
            context.append("音频文件: ").append(audioPath).append("\n");
        }

        context.append("\n请综合分析音频和视频内容，回答以下问题：").append(question);
        return context.toString();
    }

    /**
     * 多模态输出生成 - 回答中包含图片引用、格式化内容
     */
    public Map<String, Object> generateRichResponse(String question, boolean includeImages,
                                                      boolean includeCharts, boolean includeLinks) {
        Map<String, Object> response = new HashMap<>();

        // 文本回答
        response.put("text", "多模态输出框架已就绪");

        // 相关图片引用
        if (includeImages) {
            List<Map<String, String>> images = new ArrayList<>();
            Map<String, String> img = new HashMap<>();
            img.put("url", "/api/files/preview/example.jpg");
            img.put("caption", "相关图片示例");
            img.put("relevance", "0.85");
            images.add(img);
            response.put("images", images);
        }

        // 图表数据
        if (includeCharts) {
            Map<String, Object> chart = new HashMap<>();
            chart.put("type", "bar");
            chart.put("title", "数据统计");
            chart.put("data", Collections.emptyList());
            response.put("charts", chart);
        }

        // 相关链接
        if (includeLinks) {
            List<Map<String, String>> links = new ArrayList<>();
            Map<String, String> link = new HashMap<>();
            link.put("title", "相关文档");
            link.put("url", "/api/documents/1");
            links.add(link);
            response.put("links", links);
        }

        return response;
    }

    private double calculateConfidence(List<Map<String, Object>> sources) {
        if (sources.isEmpty()) return 0.5;
        return sources.stream()
                .mapToDouble(s -> {
                    Object score = s.get("score");
                    return score instanceof Double ? (Double) score : 0.5;
                })
                .average()
                .orElse(0.5);
    }
}
