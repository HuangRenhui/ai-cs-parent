package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 多媒体 OCR / ASR 服务（占位）
 *
 * <p>TODO 后续实现：
 * <ul>
 *   <li>{@link #performImageOcr}：本地图像预处理（灰度化 → 按阈值二值化 → 文字区域检测）后输出 {@link OcrResult}；
 *       生产环境应集成 Tesseract/PaddleOCR；</li>
 *   <li>{@link #performRemoteOcr}：把图片转 Base64 调远程 OCR API 并解析为 {@link OcrResult}；</li>
 *   <li>{@link #performAudioAsr}：音频转写为文字；</li>
 *   <li>{@link #transcribeWithWhisper}：调用 Whisper 兼容接口转写。</li>
 * </ul>
 * </p>
 *
 * <p>当前不做图像预处理、不发起任何远程调用：四个方法一律抛 {@link IOException}（占位），
 * 上层按异常分支降级；两个结果类型（{@link OcrResult}、{@link TextRegion}）为结果载体。</p>
 */
@Slf4j
@Service
public class MediaOcrService {

    /**
     * OCR结果
     */
    public static class OcrResult {
        /** 识别出的完整文本 */
        private String text;
        /** 整体置信度（0~1） */
        private double confidence;
        /** 检测到的文字区域列表 */
        private List<TextRegion> regions;

        public OcrResult() {
            this.text = "";
            this.confidence = 0;
            this.regions = new ArrayList<>();
        }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public List<TextRegion> getRegions() { return regions; }
        public void setRegions(List<TextRegion> regions) { this.regions = regions; }
    }

    /**
     * 文字区域
     */
    public static class TextRegion {
        /** 该区域识别出的文本 */
        private String text;
        /** 文字区域在图片中的位置（矩形框） */
        private Rectangle boundingBox;
        /** 该区域识别置信度（0~1） */
        private double confidence;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public Rectangle getBoundingBox() { return boundingBox; }
        public void setBoundingBox(Rectangle boundingBox) { this.boundingBox = boundingBox; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }

    /**
     * 图片 OCR（占位：抛 IOException）
     *
     * @param imageFile 图片文件
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public OcrResult performImageOcr(File imageFile) throws IOException {
        log.warn("[占位] 图片 OCR 未实现");
        throw new IOException("图片 OCR 为占位实现");
    }

    /**
     * 远程 OCR（占位：不发起 HTTP 调用）
     *
     * @param apiUrl 远程 API 地址
     * @param apiKey 密钥
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public OcrResult performRemoteOcr(File imageFile, String apiUrl, String apiKey) throws IOException {
        log.warn("[占位] 远程 OCR 未实现");
        throw new IOException("远程 OCR 为占位实现");
    }

    /**
     * 音频 ASR 转写（占位：抛 IOException）
     *
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String performAudioAsr(File audioFile) throws IOException {
        log.warn("[占位] 音频 ASR 未实现");
        throw new IOException("音频 ASR 为占位实现");
    }

    /**
     * Whisper 转写（占位：不发起 HTTP 调用）
     *
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String transcribeWithWhisper(File audioFile, String whisperApiUrl) throws IOException {
        log.warn("[占位] Whisper 转写未实现");
        throw new IOException("Whisper 转写为占位实现");
    }
}
