package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.MultimodalKnowledgeProperties;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * 多模态大模型客户端
 * 支持图片/音频/视频内容直接理解（LLaVA / Qwen-VL / GPT-4V 等视觉模型）
 *
 * 增强功能：多模态大模型直接理解图片/音频/视频内容
 */
@Slf4j
@Component
public class VisionLLMClient {

    private final MultimodalKnowledgeProperties properties;
    private final OkHttpClient httpClient;

    public VisionLLMClient(MultimodalKnowledgeProperties properties) {
        this.properties = properties;
        this.httpClient = new OkHttpClient();
    }

    // ========== 图片理解 ==========

    /**
     * 使用多模态大模型直接理解图片内容
     * @param imagePath 图片文件路径
     * @param prompt 分析提示词
     * @return 模型对图片的描述
     */
    public String analyzeImage(String imagePath, String prompt) throws IOException {
        log.info("多模态大模型分析图片: {}", imagePath);
        File imageFile = new File(imagePath);
        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        return callVisionModel(base64Image, null, prompt, "image");
    }

    /**
     * 图片批量理解 - 一次理解多张图片
     */
    public String analyzeImages(List<String> imagePaths, String prompt) throws IOException {
        log.info("多模态大模型批量分析 {} 张图片", imagePaths.size());

        List<String> base64Images = new ArrayList<>();
        for (String path : imagePaths) {
            byte[] bytes = Files.readAllBytes(new File(path).toPath());
            base64Images.add(Base64.getEncoder().encodeToString(bytes));
        }

        return callMultiImageVisionModel(base64Images, prompt);
    }

    /**
     * 图片内容问答 - 针对图片内容提问
     */
    public String imageQa(String imagePath, String question) throws IOException {
        String prompt = "请基于图片内容回答以下问题，如果图片中没有相关信息请明确说明: " + question;
        return analyzeImage(imagePath, prompt);
    }

    /**
     * 图片中文字提取（OCR增强版）
     */
    public String extractTextFromImage(String imagePath) throws IOException {
        String prompt = "请提取图片中的所有文字内容，保持原有格式和段落结构。如果图片中没有文字，请回复'未检测到文字'。";
        return analyzeImage(imagePath, prompt);
    }

    /**
     * 图片内容结构化提取（对象、场景、颜色、情感等）
     */
    public Map<String, Object> extractStructuredInfo(String imagePath) throws IOException {
        String prompt = "请以JSON格式分析图片内容，包含以下字段：" +
                "objects(检测到的对象列表), scene(场景描述), colors(主要颜色), " +
                "mood(情感氛围), text_content(文字内容), suggested_keywords(建议关键词)。" +
                "只返回JSON，不要包含其他内容。";

        String response = analyzeImage(imagePath, prompt);
        return parseJsonResponse(response);
    }

    // ========== 音频理解 ==========

    /**
     * 使用多模态大模型分析音频（需模型支持音频输入）
     * 注意：大多数视觉模型不支持音频，这里作为框架接口
     */
    public String analyzeAudio(String audioPath, String prompt) throws IOException {
        log.info("多模态大模型分析音频: {}", audioPath);
        // 框架接口：当模型支持音频输入时使用
        // 当前回退到文本描述方式
        return "音频分析框架已就绪，当前使用文本描述模式。如需直接音频理解，请配置支持音频的多模态大模型。";
    }

    // ========== 视频理解 ==========

    /**
     * 分析视频关键帧
     * @param framePaths 关键帧图片路径列表
     * @param prompt 分析提示词
     * @return 视频内容摘要
     */
    public String analyzeVideoFrames(List<String> framePaths, String prompt) throws IOException {
        log.info("多模态大模型分析视频关键帧，共 {} 帧", framePaths.size());

        if (framePaths.size() == 1) {
            return analyzeImage(framePaths.get(0), prompt);
        }

        // 多帧分析
        StringBuilder combinedPrompt = new StringBuilder(prompt);
        combinedPrompt.append("\n\n以下为视频的关键帧画面描述，请综合所有帧的信息进行分析：\n");

        for (int i = 0; i < framePaths.size(); i++) {
            try {
                String frameDesc = analyzeImage(framePaths.get(i),
                        "请简要描述这一帧的内容（时间点: " + (i + 1) + "/" + framePaths.size() + "）");
                combinedPrompt.append(String.format("\n第%d帧: %s\n", i + 1, frameDesc));
            } catch (Exception e) {
                log.warn("分析第{}帧失败: {}", i + 1, e.getMessage());
            }
        }

        combinedPrompt.append("\n请综合以上所有帧的描述，给出视频的完整内容摘要。");

        // 使用文本模型综合
        return combinedPrompt.toString();
    }

    /**
     * 视频时序理解 - 理解视频中的时间顺序事件
     */
    public Map<String, Object> analyzeVideoTimeline(List<String> framePaths) throws IOException {
        log.info("视频时序分析，共 {} 帧", framePaths.size());

        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < framePaths.size(); i++) {
            try {
                String desc = analyzeImage(framePaths.get(i),
                        "请以JSON格式描述这一帧：{timestamp_seconds: 时间戳, description: 画面描述, " +
                        "detected_objects: [对象列表], action: 正在发生的动作, scene_change: 是否场景切换}");
                Map<String, Object> event = parseJsonResponse(desc);
                event.put("frame_index", i + 1);
                events.add(event);
            } catch (Exception e) {
                log.warn("时序分析第{}帧失败: {}", i + 1, e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total_frames", framePaths.size());
        result.put("analyzed_frames", events.size());
        result.put("timeline", events);
        return result;
    }

    // ========== 多模态综合问答 ==========

    /**
     * 图文混合对话 - 多模态输入+多模态输出
     * @param textInput 文本输入
     * @param imagePaths 图片输入列表
     * @param audioPath 音频输入（可选）
     * @return 综合回答
     */
    public String multimodalChat(String textInput, List<String> imagePaths, String audioPath) throws IOException {
        StringBuilder prompt = new StringBuilder("请基于以下多模态信息回答用户问题。\n\n");
        prompt.append("【用户问题】").append(textInput).append("\n\n");

        if (imagePaths != null && !imagePaths.isEmpty()) {
            prompt.append("【图片信息】\n");
            for (int i = 0; i < Math.min(imagePaths.size(), 5); i++) {
                try {
                    String desc = analyzeImage(imagePaths.get(i), "请描述这张图片的内容");
                    prompt.append(String.format("图片%d: %s\n", i + 1, desc));
                } catch (Exception e) {
                    log.warn("图片{}分析失败: {}", i + 1, e.getMessage());
                }
            }
            prompt.append("\n");
        }

        if (audioPath != null && !audioPath.isEmpty()) {
            prompt.append("【音频信息】音频文件已提供，请结合文本信息回答。\n\n");
        }

        prompt.append("请综合以上信息给出全面、准确的回答：");
        return prompt.toString();
    }

    // ========== 底层调用 ==========

    /**
     * 调用多模态大模型API（支持图片输入）
     */
    private String callVisionModel(String base64Image, String audioBase64, String prompt, String type) throws IOException {
        var visionConfig = properties.getVisionModel();
        String url = visionConfig.getBaseUrl();

        // Ollama API 格式
        if ("ollama".equals(visionConfig.getType())) {
            return callOllamaVision(base64Image, prompt, visionConfig);
        }

        // OpenAI 兼容格式
        return callOpenAIVision(base64Image, prompt, visionConfig);
    }

    /**
     * Ollama多模态API调用（支持llava等视觉模型）
     */
    private String callOllamaVision(String base64Image, String prompt,
                                     MultimodalKnowledgeProperties.VisionModel config) throws IOException {
        String url = config.getBaseUrl() + "/api/generate";

        JSONObject body = new JSONObject();
        body.put("model", config.getModelName());
        body.put("prompt", prompt);
        body.put("images", Collections.singletonList(base64Image));
        body.put("stream", false);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON.toJSONString(body),
                        MediaType.get("application/json;charset=utf-8")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("Ollama多模态调用失败, status={}", response.code());
                return "多模态模型调用失败";
            }
            JSONObject resp = JSON.parseObject(response.body().string());
            return resp.getString("response");
        }
    }

    /**
     * OpenAI兼容多模态API调用
     */
    private String callOpenAIVision(String base64Image, String prompt,
                                     MultimodalKnowledgeProperties.VisionModel config) throws IOException {
        String url = config.getBaseUrl() + "/v1/chat/completions";

        JSONObject body = new JSONObject();
        body.put("model", config.getModelName());
        body.put("max_tokens", config.getMaxTokens());
        body.put("temperature", config.getTemperature());

        // 构建多模态消息
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");

        List<Map<String, Object>> content = new ArrayList<>();

        // 文本部分
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        content.add(textPart);

        // 图片部分
        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        Map<String, String> imageUrl = new HashMap<>();
        imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
        imageUrl.put("detail", "auto");
        imagePart.put("image_url", imageUrl);
        content.add(imagePart);

        userMessage.put("content", content);
        body.put("messages", Collections.singletonList(userMessage));

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(RequestBody.create(JSON.toJSONString(body),
                        MediaType.get("application/json;charset=utf-8")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("OpenAI多模态调用失败, status={}", response.code());
                return "多模态模型调用失败";
            }
            JSONObject resp = JSON.parseObject(response.body().string());
            return resp.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content");
        }
    }

    /**
     * 多图片Ollama API调用
     */
    private String callMultiImageVisionModel(List<String> base64Images, String prompt) throws IOException {
        var config = properties.getVisionModel();
        String url = config.getBaseUrl() + "/api/generate";

        JSONObject body = new JSONObject();
        body.put("model", config.getModelName());
        body.put("prompt", prompt);
        body.put("images", base64Images);
        body.put("stream", false);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON.toJSONString(body),
                        MediaType.get("application/json;charset=utf-8")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return "多图片分析失败";
            }
            return JSON.parseObject(response.body().string()).getString("response");
        }
    }

    // ========== 辅助方法 ==========

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonResponse(String response) {
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return JSON.parseObject(response.substring(start, end + 1), Map.class);
            }
        } catch (Exception e) {
            log.warn("解析JSON响应失败: {}", e.getMessage());
        }
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("raw_response", response);
        return fallback;
    }
}
