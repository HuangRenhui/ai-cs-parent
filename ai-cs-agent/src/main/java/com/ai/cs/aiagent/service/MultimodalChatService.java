package com.ai.cs.aiagent.service;

import com.ai.cs.common.llm.ModelCallException;
import com.ai.cs.common.llm.ModelRouter;
import com.ai.cs.common.llm.ModelTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 多模态对话服务：图片理解、语音转写、TTS、视频理解
 * 补全功能清单 P2 半成品「MultimodalChatService为占位桩」
 * 已登记VISION/ASR/TTS类型模型时走模型调用，未登记时返回不支持提示
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
     * 图片理解：已登记VISION模型时调用视觉模型，否则返回不支持
     */
    public String chatWithImage(String imageUrl, String question) {
        if (!StringUtils.hasText(imageUrl)) {
            return "请提供图片地址";
        }
        try {
            String prompt = String.format(
                    "请根据图片描述回答问题。图片地址：%s\n问题：%s",
                    imageUrl, question == null ? "请描述这张图片" : question);
            return modelRouter.chatForType(ModelTypeEnum.VISION.getCode(),
                    List.of(Map.of("role", "user", "content", prompt)));
        } catch (ModelCallException e) {
            log.info("视觉模型不可用，返回不支持提示: {}", e.getMessage());
            return UNSUPPORTED;
        }
    }

    /**
     * 语音转文字：已登记ASR模型时调用，否则返回不支持
     */
    public String speechToText(String audioUrl) {
        if (!StringUtils.hasText(audioUrl)) {
            return "请提供音频地址";
        }
        try {
            String prompt = "请将以下语音转写为文字。音频地址：" + audioUrl;
            return modelRouter.chatForType(ModelTypeEnum.ASR.getCode(),
                    List.of(Map.of("role", "user", "content", prompt)));
        } catch (ModelCallException e) {
            log.info("ASR模型不可用，返回不支持提示: {}", e.getMessage());
            return UNSUPPORTED;
        }
    }

    /**
     * 文字转语音：已登记TTS模型时调用，否则返回不支持
     */
    public String textToSpeech(String text) {
        if (!StringUtils.hasText(text)) {
            return "请提供要转换的文字";
        }
        try {
            String prompt = "请将以下文字转为语音输出：" + text;
            return modelRouter.chatForType(ModelTypeEnum.TTS.getCode(),
                    List.of(Map.of("role", "user", "content", prompt)));
        } catch (ModelCallException e) {
            log.info("TTS模型不可用，返回不支持提示: {}", e.getMessage());
            return UNSUPPORTED;
        }
    }

    /**
     * 视频理解：已登记MULTIMODAL模型时调用，否则返回不支持
     */
    public String chatWithVideo(String videoUrl, String question) {
        if (!StringUtils.hasText(videoUrl)) {
            return "请提供视频地址";
        }
        try {
            String prompt = String.format(
                    "请根据视频内容回答问题。视频地址：%s\n问题：%s",
                    videoUrl, question == null ? "请描述这个视频" : question);
            return modelRouter.chatForType(ModelTypeEnum.MULTIMODAL.getCode(),
                    List.of(Map.of("role", "user", "content", prompt)));
        } catch (ModelCallException e) {
            log.info("多模态模型不可用，返回不支持提示: {}", e.getMessage());
            return UNSUPPORTED;
        }
    }
}