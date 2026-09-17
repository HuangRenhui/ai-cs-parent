package com.ai.cs.aiagent.service;

import com.ai.cs.common.llm.ModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 多模态对话服务（占位）：图片理解、语音转写、TTS、视频理解
 *
 * <p>TODO 后续实现：已登记 VISION/ASR/TTS/MULTIMODAL 类型模型时走 {@code ModelRouter.chatForType}，
 * 模型不可用（{@code ModelCallException}）时统一返回 {@link #UNSUPPORTED}。</p>
 *
 * <p>当前不调用任何模型，四个能力一律返回 {@link #UNSUPPORTED}（明确告知不支持，而非伪装成功）。</p>
 *
 * @author huangrenhui
 */
@Slf4j
@Service
public class MultimodalChatService {

    public static final String UNSUPPORTED =
            "当前版本不支持该多模态能力，请使用文字描述问题，或转人工客服。";

    @Resource
    private ModelRouter modelRouter;

    /**
     * 图片理解（占位：不调用视觉模型）
     *
     * @param imageUrl 图片地址
     * @param question 针对图片的问题
     * @return 不支持提示
     */
    public String chatWithImage(String imageUrl, String question) {
        log.info("[占位] 图片理解未实现 imageUrl={}", imageUrl);
        return UNSUPPORTED;
    }

    /**
     * 语音转文字（占位：不调用 ASR 模型）
     *
     * @param audioUrl 音频地址
     * @return 不支持提示
     */
    public String speechToText(String audioUrl) {
        log.info("[占位] 语音转写未实现 audioUrl={}", audioUrl);
        return UNSUPPORTED;
    }

    /**
     * 文字转语音（占位：不调用 TTS 模型）
     *
     * @param text 待转换文字
     * @return 不支持提示
     */
    public String textToSpeech(String text) {
        log.info("[占位] 文字转语音未实现");
        return UNSUPPORTED;
    }

    /**
     * 视频理解（占位：不调用多模态模型）
     *
     * @param videoUrl 视频地址
     * @param question 针对视频的问题
     * @return 不支持提示
     */
    public String chatWithVideo(String videoUrl, String question) {
        log.info("[占位] 视频理解未实现 videoUrl={}", videoUrl);
        return UNSUPPORTED;
    }
}
