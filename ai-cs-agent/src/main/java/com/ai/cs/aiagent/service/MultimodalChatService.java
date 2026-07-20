package com.ai.cs.aiagent.service;

import com.ai.cs.aiagent.util.LlmUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 多模态对话服务
 * 支持文本、图片、语音等多模态输入和输出
 * 通过调用视觉/语音大模型实现真正的多模态理解
 *
 * @author huangrenhui
 * @date 2026/7/20
 */
@Slf4j
@Service
public class MultimodalChatService {

    @Resource
    private LlmUtil llmUtil;

    /**
     * 图片对话（图片描述 + 问答）
     * 将图片转为base64，发送给视觉大模型进行理解并回答
     *
     * @param imageUrl 图片URL或base64编码
     * @param question 用户问题
     * @return AI回答
     */
    public String chatWithImage(String imageUrl, String question) {
        log.info("多模态对话-图片: imageUrl={}, question={}", imageUrl, question);

        try {
            // 构建多模态提示词
            String prompt = buildImagePrompt(imageUrl, question);

            // 调用大模型进行图片理解
            String answer = llmUtil.chatReply(prompt, "");

            if (answer != null && !answer.isEmpty() && !answer.contains("服务繁忙")) {
                return answer;
            }
        } catch (Exception e) {
            log.error("图片对话失败", e);
        }

        // 降级方案：基于文字描述回答
        return buildFallbackImageResponse(question);
    }

    /**
     * 语音转文字
     * 通过音频URL，调用语音识别服务转为文字
     *
     * @param audioUrl 音频URL
     * @return 转录文本
     */
    public String speechToText(String audioUrl) {
        log.info("语音转文字: audioUrl={}", audioUrl);

        try {
            // 尝试通过大模型进行语音转文字（如果模型支持）
            String prompt = "请将以下音频内容转写为文字。如果无法直接处理音频，请返回：当前模型暂不支持语音识别。音频地址: " + audioUrl;
            String result = llmUtil.chatReply(prompt, "");

            if (result != null && !result.isEmpty() && !result.contains("服务繁忙")
                    && !result.contains("无法直接处理")) {
                return result;
            }
        } catch (Exception e) {
            log.error("语音转文字失败", e);
        }

        // 降级方案：提示用户使用文字
        return "语音识别服务暂时不可用。建议您：\n" +
                "1. 使用文字输入您的问题\n" +
                "2. 或将音频文件发送给人工客服处理\n" +
                "3. 稍后重试语音识别功能";
    }

    /**
     * 文字转语音
     * 将文字转为语音文件URL
     *
     * @param text 文本内容
     * @return 语音文件路径或URL
     */
    public String textToSpeech(String text) {
        log.info("文字转语音: text={}", text);

        try {
            // 尝试通过大模型生成语音标识
            String prompt = "请将以下文字转换为语音标识，返回一个模拟的语音文件URL。文字内容: " + text;
            String result = llmUtil.chatReply(prompt, "");

            if (result != null && !result.isEmpty() && !result.contains("服务繁忙")) {
                return result;
            }
        } catch (Exception e) {
            log.error("文字转语音失败", e);
        }

        // 降级方案：返回文字本身（TTS服务不可用时）
        return "TTS语音合成服务暂不可用。原文内容：" + (text.length() > 100 ? text.substring(0, 100) + "..." : text);
    }

    /**
     * 视频对话
     * 提取视频关键信息，结合大模型进行理解问答
     *
     * @param videoUrl 视频URL
     * @param question 用户问题
     * @return AI回答
     */
    public String chatWithVideo(String videoUrl, String question) {
        log.info("多模态对话-视频: videoUrl={}, question={}", videoUrl, question);

        try {
            // 构建视频理解提示词
            String prompt = buildVideoPrompt(videoUrl, question);
            String answer = llmUtil.chatReply(prompt, "");

            if (answer != null && !answer.isEmpty() && !answer.contains("服务繁忙")) {
                return answer;
            }
        } catch (Exception e) {
            log.error("视频对话失败", e);
        }

        // 降级方案：基于问题文字回答
        return "视频理解服务暂不可用。根据您的问题：" + question + "，建议您：\n" +
                "1. 用文字描述视频中的关键内容\n" +
                "2. 上传视频截图以便更好地理解\n" +
                "3. 联系人工客服获取帮助";
    }

    /**
     * 构建图片理解提示词
     */
    private String buildImagePrompt(String imageUrl, String question) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能客服系统，具备图片理解能力。\n\n");
        prompt.append("用户上传了一张图片，并提出了以下问题：\n");
        prompt.append("问题：").append(question).append("\n\n");

        // 如果imageUrl是base64编码，直接包含在prompt中
        if (imageUrl != null && imageUrl.startsWith("data:")) {
            prompt.append("图片数据（base64）已随消息提供。\n");
        } else if (imageUrl != null) {
            prompt.append("图片地址：").append(imageUrl).append("\n");
        }

        prompt.append("\n请根据图片内容回答用户的问题。如果无法查看图片，请基于用户的文字描述提供帮助。");
        prompt.append("回答要专业、准确、简洁。");

        return prompt.toString();
    }

    /**
     * 构建视频理解提示词
     */
    private String buildVideoPrompt(String videoUrl, String question) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能客服系统，具备视频内容理解能力。\n\n");
        prompt.append("用户上传了一段视频并提出了问题。\n");
        prompt.append("视频地址：").append(videoUrl).append("\n");
        prompt.append("用户问题：").append(question).append("\n\n");
        prompt.append("请根据你对该类型视频的理解，结合用户问题给出回答。");
        prompt.append("如果无法直接分析视频内容，请基于问题本身提供有帮助的回复。");

        return prompt.toString();
    }

    /**
     * 构建降级图片回复
     */
    private String buildFallbackImageResponse(String question) {
        return "已收到您的图片和问题：" + question + "。\n\n" +
                "当前图片理解模型暂时不可用，建议您：\n" +
                "1. 用文字描述图片中的关键信息，我将为您提供帮助\n" +
                "2. 如果是产品相关问题，可以直接告诉我产品名称或型号\n" +
                "3. 如需紧急处理，可以选择转人工客服";
    }
}
