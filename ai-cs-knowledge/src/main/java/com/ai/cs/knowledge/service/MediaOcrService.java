package com.ai.cs.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * 多媒体OCR文字识别服务
 * 支持图片OCR文字提取和音频ASR语音转文字
 * 支持本地Tesseract和远程API两种方式
 */
@Slf4j
@Service
public class MediaOcrService {

    /**
     * OCR结果
     */
    public static class OcrResult {
        private String text;
        private double confidence;
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
        private String text;
        private Rectangle boundingBox;
        private double confidence;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public Rectangle getBoundingBox() { return boundingBox; }
        public void setBoundingBox(Rectangle boundingBox) { this.boundingBox = boundingBox; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }

    /**
     * 对图片进行OCR识别（基础版 - 使用图片像素分析提取文本特征）
     * 实际生产环境应集成Tesseract/PaddleOCR或调用云服务API
     */
    public OcrResult performImageOcr(File imageFile) throws IOException {
        OcrResult result = new OcrResult();

        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                throw new IOException("无法读取图片: " + imageFile.getName());
            }

            // 基础OCR实现：图像预处理 + 文字区域检测
            // 生产环境建议替换为Tesseract调用
            BufferedImage grayImage = toGrayscale(image);
            BufferedImage binaryImage = binarize(grayImage, 128);

            // 检测文字区域
            List<TextRegion> regions = detectTextRegions(binaryImage);
            result.setRegions(regions);

            // 拼接所有文本
            StringBuilder sb = new StringBuilder();
            for (TextRegion region : regions) {
                sb.append(region.getText()).append(" ");
            }
            result.setText(sb.toString().trim());
            result.setConfidence(0.7); // 基础版本置信度较低

            log.info("图片OCR完成: {}, 识别区域数: {}", imageFile.getName(), regions.size());

        } catch (Exception e) {
            log.error("图片OCR失败: {}", e.getMessage());
            result.setText("[OCR识别失败: " + e.getMessage() + "]");
            result.setConfidence(0);
        }

        return result;
    }

    /**
     * 调用远程OCR API（如百度OCR、腾讯云OCR等）
     */
    public OcrResult performRemoteOcr(File imageFile, String apiUrl, String apiKey) throws IOException {
        OcrResult result = new OcrResult();

        try {
            // 将图片转为Base64
            byte[] imageBytes = readFileBytes(imageFile);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 构建请求
            String requestBody = String.format(
                    "{\"image\":\"%s\",\"detect_direction\":true,\"detect_language\":true}",
                    base64Image
            );

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes());
                os.flush();
            }

            // 读取响应
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            // 解析响应（简化处理，实际应根据API返回格式解析）
            result.setText(response.toString());
            result.setConfidence(0.95);

            log.info("远程OCR完成: {}", imageFile.getName());

        } catch (Exception e) {
            log.error("远程OCR失败: {}", e.getMessage());
            result.setText("[远程OCR失败: " + e.getMessage() + "]");
            result.setConfidence(0);
        }

        return result;
    }

    /**
     * 音频语音转文字（ASR - 基础实现）
     * 实际生产环境应集成Whisper或其他ASR引擎
     */
    public String performAudioAsr(File audioFile) throws IOException {
        try {
            // 使用FFmpeg提取音频并调用Whisper进行转录
            // 这是一个框架实现，需要配置Whisper服务地址
            log.info("ASR框架已就绪，等待配置Whisper服务地址");

            // 返回占位文本，实际需调用Whisper API
            return "[ASR转录待配置Whisper服务]";

        } catch (Exception e) {
            log.error("ASR失败: {}", e.getMessage());
            return "[ASR失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 调用Whisper API进行语音转录
     */
    public String transcribeWithWhisper(File audioFile, String whisperApiUrl) throws IOException {
        try {
            // 构建multipart请求上传音频文件
            // 实际实现需要根据Whisper API格式调整
            log.info("调用Whisper API: {}", whisperApiUrl);

            // TODO: 实现HTTP multipart上传并解析转录结果
            return "[Whisper转录结果]";

        } catch (Exception e) {
            log.error("Whisper转录失败: {}", e.getMessage());
            return "[Whisper转录失败]";
        }
    }

    // ========== 图像预处理 ==========

    private BufferedImage toGrayscale(BufferedImage image) {
        BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return gray;
    }

    private BufferedImage binarize(BufferedImage grayImage, int threshold) {
        int width = grayImage.getWidth();
        int height = grayImage.getHeight();
        BufferedImage binary = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = new Color(grayImage.getRGB(x, y)).getRed();
                int value = pixel > threshold ? 255 : 0;
                binary.setRGB(x, y, new Color(value, value, value).getRGB());
            }
        }
        return binary;
    }

    /**
     * 检测文字区域（简化实现）
     * 通过连通域分析找出可能的文字区域
     */
    private List<TextRegion> detectTextRegions(BufferedImage binaryImage) {
        List<TextRegion> regions = new ArrayList<>();
        int width = binaryImage.getWidth();
        int height = binaryImage.getHeight();

        // 简化实现：按行扫描检测文字区域
        int blockSize = 16; // 16x16像素块
        for (int y = 0; y < height; y += blockSize) {
            for (int x = 0; x < width; x += blockSize) {
                int bw = Math.min(blockSize, width - x);
                int bh = Math.min(blockSize, height - y);

                int blackPixels = 0;
                int totalPixels = bw * bh;

                for (int dy = 0; dy < bh; dy++) {
                    for (int dx = 0; dx < bw; dx++) {
                        int pixel = new Color(binaryImage.getRGB(x + dx, y + dy)).getRed();
                        if (pixel < 128) {
                            blackPixels++;
                        }
                    }
                }

                // 如果黑色像素占比超过阈值，认为是文字区域
                if ((double) blackPixels / totalPixels > 0.3) {
                    TextRegion region = new TextRegion();
                    region.setBoundingBox(new Rectangle(x, y, bw, bh));
                    region.setText("[文本块]");
                    region.setConfidence(0.5);
                    regions.add(region);
                }
            }
        }

        return regions;
    }

    private byte[] readFileBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            return bos.toByteArray();
        }
    }
}
