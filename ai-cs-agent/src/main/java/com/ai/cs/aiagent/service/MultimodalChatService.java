package com.ai.cs.aiagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 多模态接口占位。当前对话主路径仅支持文本，明确返回不支持，避免把 URL 喂给文本模型冒充理解图片/语音。
 */
@Slf4j
@Service
public class MultimodalChatService {

    public static final String UNSUPPORTED =
            "当前版本不支持该多模态能力，请使用文字描述问题，或转人工客服。";

    public String chatWithImage(String imageUrl, String question) {
        log.info("多模态对话-图片尚未接入视觉模型 question={}", question);
        return UNSUPPORTED;
    }

    public String speechToText(String audioUrl) {
        log.info("语音转文字尚未接入 ASR audioUrl={}", audioUrl);
        return UNSUPPORTED;
    }

    public String textToSpeech(String text) {
        log.info("文字转语音尚未接入 TTS");
        return UNSUPPORTED;
    }

    public String chatWithVideo(String videoUrl, String question) {
        log.info("多模态对话-视频尚未接入 question={}", question);
        return UNSUPPORTED;
    }
}
