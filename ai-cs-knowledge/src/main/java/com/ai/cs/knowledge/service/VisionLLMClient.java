package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.MultimodalKnowledgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 多模态大模型客户端（占位）
 *
 * <p>TODO 后续实现：按 {@code MultimodalKnowledgeProperties.visionModel} 配置
 * 调 Ollama（{@code /api/generate}，images 传 Base64）或 OpenAI 兼容
 * （{@code /v1/chat/completions} + Bearer 鉴权）多模态接口，支持图片理解、
 * 多图比较、图片文字提取、结构化信息抽取、视频关键帧时序理解与图文混合问答；
 * 并解析模型返回的 JSON（含降级 raw_response）。</p>
 *
 * <p>当前不读文件、不发 HTTP：九个公开入口一律抛 {@link IOException}
 * （与服务不可用语义一致），上层按异常分支降级。</p>
 */
@Slf4j
@Component
public class VisionLLMClient {

    private final MultimodalKnowledgeProperties properties;

    public VisionLLMClient(MultimodalKnowledgeProperties properties) {
        this.properties = properties;
    }

    // ========== 图片理解 ==========

    /**
     * 使用多模态大模型直接理解图片内容（占位：抛 IOException）
     *
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String analyzeImage(String imagePath, String prompt) throws IOException {
        log.warn("[占位] 多模态大模型图片理解未实现 imagePath={}", imagePath);
        throw new IOException("多模态大模型调用为占位实现，后端未接入视觉模型");
    }

    /**
     * 图片批量理解（占位：抛 IOException）
     *
     * @throws IOException 占位实现
     */
    public String analyzeImages(List<String> imagePaths, String prompt) throws IOException {
        log.warn("[占位] 多模态大模型批量图片理解未实现 count={}", imagePaths == null ? 0 : imagePaths.size());
        throw new IOException("多模态大模型调用为占位实现，后端未接入视觉模型");
    }

    /**
     * 图片内容问答（占位：抛 IOException）
     *
     * @throws IOException 占位实现
     */
    public String imageQa(String imagePath, String question) throws IOException {
        log.warn("[占位] 图片内容问答未实现 imagePath={}", imagePath);
        throw new IOException("多模态大模型调用为占位实现，后端未接入视觉模型");
    }

    /**
     * 图片中文字提取（占位：抛 IOException）
     *
     * @throws IOException 占位实现
     */
    public String extractTextFromImage(String imagePath) throws IOException {
        log.warn("[占位] 图片文字提取未实现 imagePath={}", imagePath);
        throw new IOException("多模态大模型调用为占位实现，后端未接入视觉模型");
    }

    /**
     * 图片内容结构化提取（占位：抛 IOException）
     *
     * @throws IOException 占位实现
     */
    public Map<String, Object> extractStructuredInfo(String imagePath) throws IOException {
        log.warn("[占位] 图片结构化信息提取未实现 imagePath={}", imagePath);
        throw new IOException("多模态大模型调用为占位实现，后端未接入视觉模型");
    }

    // ========== 音频理解 ==========

    /**
     * 音频理解（占位：抛 IOException）
     *
     * @throws IOException 占位实现
     */
    public String analyzeAudio(String audioPath, String prompt) throws IOException {
        log.warn("[占位] 音频多模态理解未实现 audioPath={}", audioPath);
        throw new IOException("多模态大模型调用为占位实现，后端未接入音频理解模型");
    }

    // ========== 视频理解 ==========

    /**
     * 分析视频关键帧（占位：抛 IOException）
     *
     * @throws IOException 占位实现
     */
    public String analyzeVideoFrames(List<String> framePaths, String prompt) throws IOException {
        log.warn("[占位] 视频关键帧理解未实现 count={}", framePaths == null ? 0 : framePaths.size());
        throw new IOException("多模态大模型调用为占位实现，后端未接入视觉模型");
    }

    /**
     * 视频时序理解（占位：抛 IOException）
     *
     * @throws IOException 占位实现
     */
    public Map<String, Object> analyzeVideoTimeline(List<String> framePaths) throws IOException {
        log.warn("[占位] 视频时序理解未实现 count={}", framePaths == null ? 0 : framePaths.size());
        throw new IOException("多模态大模型调用为占位实现，后端未接入视觉模型");
    }

    // ========== 多模态综合问答 ==========

    /**
     * 图文混合对话（占位：抛 IOException）
     *
     * @throws IOException 占位实现
     */
    public String multimodalChat(String textInput, List<String> imagePaths, String audioPath) throws IOException {
        log.warn("[占位] 多模态综合问答未实现 imageCount={}", imagePaths == null ? 0 : imagePaths.size());
        throw new IOException("多模态大模型调用为占位实现，后端未接入视觉模型");
    }
}
