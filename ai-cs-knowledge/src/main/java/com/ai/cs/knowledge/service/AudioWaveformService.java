package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.AudioProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 真实波形图生成服务
 * 使用FFmpeg分析真实音频数据生成精确波形图
 * 替代原来的随机占位波形图实现
 */
@Slf4j
@Service
public class AudioWaveformService {

    private final AudioProperties audioProperties;

    public AudioWaveformService(AudioProperties audioProperties) {
        this.audioProperties = audioProperties;
    }

    /**
     * 生成真实波形图（使用FFmpeg命令行工具）
     * 这是最准确的方法，利用FFmpeg的showwaves滤镜
     */
    public String generateWaveformWithFFmpeg(File audioFile, String fileId) throws IOException {
        String waveformFileName = fileId + "_waveform.png";
        Path waveformPath = Paths.get(audioProperties.getStoragePath(), waveformFileName);

        try {
            // 使用FFmpeg生成波形图
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", audioFile.getAbsolutePath(),
                    "-filter_complex", "showwavespic=s=" + 
                            audioProperties.getWaveformWidth() + "x" + 
                            audioProperties.getWaveformHeight() + 
                            ":colors=#4285F4",
                    "-frames:v", "1",
                    "-y", // 覆盖已存在的文件
                    waveformPath.toString()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取FFmpeg输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 && Files.exists(waveformPath)) {
                log.info("FFmpeg波形图生成成功: {}", waveformPath);
                return waveformPath.toString();
            } else {
                log.warn("FFmpeg波形图生成失败(exitCode={})，降级使用Java方案", exitCode);
                // 降级使用Java方案
                return generateWaveformWithJava(audioFile, fileId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("FFmpeg进程被中断，降级使用Java方案");
            return generateWaveformWithJava(audioFile, fileId);
        }
    }

    /**
     * 使用Java Sound API生成波形图
     * 直接读取PCM音频数据，绘制真实波形
     */
    public String generateWaveformWithJava(File audioFile, String fileId) throws IOException {
        String waveformFileName = fileId + "_waveform.png";
        Path waveformPath = Paths.get(audioProperties.getStoragePath(), waveformFileName);

        try {
            // 读取音频样本数据
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat format = audioStream.getFormat();

            // 转换为16位PCM
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(),
                    16,
                    format.getChannels(),
                    format.getChannels() * 2,
                    format.getSampleRate(),
                    false
            );

            if (!format.matches(pcmFormat)) {
                audioStream = AudioSystem.getAudioInputStream(pcmFormat, audioStream);
            }

            // 读取所有音频字节
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            byte[] audioBytes = baos.toByteArray();
            audioStream.close();

            // 转换为样本数组
            int numSamples = audioBytes.length / 2;
            short[] samples = new short[numSamples];
            ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer().get(samples);

            // 绘制波形图
            BufferedImage image = drawWaveform(samples, 
                    audioProperties.getWaveformWidth(),
                    audioProperties.getWaveformHeight());

            // 保存为PNG
            javax.imageio.ImageIO.write(image, "PNG", waveformPath.toFile());

            log.info("Java波形图生成成功: {}", waveformPath);
            return waveformPath.toString();

        } catch (UnsupportedAudioFileException e) {
            log.error("不支持的音频格式，无法生成波形图", e);
            throw new IOException("生成波形图失败: " + e.getMessage(), e);
        }
    }

    /**
     * 绘制波形图
     */
    private BufferedImage drawWaveform(short[] samples, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // 抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 背景
        g2d.setColor(new Color(30, 30, 30));
        g2d.fillRect(0, 0, width, height);

        // 绘制中心线
        g2d.setColor(new Color(60, 60, 60, 100));
        g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10, new float[]{5, 5}, 0));
        g2d.drawLine(0, height / 2, width, height / 2);

        // 计算每个像素对应的样本范围
        int samplesPerPixel = Math.max(1, samples.length / width);
        int centerY = height / 2;

        // 绘制波形
        g2d.setColor(new Color(66, 133, 244, 200));
        g2d.setStroke(new BasicStroke(1.2f));

        for (int x = 0; x < width; x++) {
            int startSample = x * samplesPerPixel;
            int endSample = Math.min(startSample + samplesPerPixel, samples.length);

            // 计算该像素范围内的最大振幅和RMS值
            int maxAmplitude = 0;
            double sumSquared = 0;
            int count = 0;

            for (int i = startSample; i < endSample; i++) {
                int amplitude = Math.abs(samples[i]);
                if (amplitude > maxAmplitude) {
                    maxAmplitude = amplitude;
                }
                sumSquared += (double) amplitude * amplitude;
                count++;
            }

            // 归一化到图像高度
            int barHeight = (int) ((double) maxAmplitude / 32768.0 * (height / 2 - 2));
            int rmsHeight = count > 0 ? (int) (Math.sqrt(sumSquared / count) / 32768.0 * (height / 2 - 2)) : 0;

            if (barHeight > 0) {
                // 绘制峰值线
                g2d.setColor(new Color(66, 133, 244, 180));
                g2d.drawLine(x, centerY - barHeight, x, centerY + barHeight);

                // 绘制RMS区域
                if (rmsHeight > 0) {
                    g2d.setColor(new Color(66, 133, 244, 80));
                    g2d.drawLine(x, centerY - rmsHeight, x, centerY + rmsHeight);
                }
            }
        }

        g2d.dispose();
        return image;
    }

    /**
     * 生成分声道波形图（左右声道分别显示）
     */
    public String generateStereoWaveform(File audioFile, String fileId) throws IOException {
        String waveformFileName = fileId + "_waveform_stereo.png";
        Path waveformPath = Paths.get(audioProperties.getStoragePath(), waveformFileName);

        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat format = audioStream.getFormat();

            if (format.getChannels() < 2) {
                // 单声道，使用标准波形图
                return generateWaveformWithJava(audioFile, fileId);
            }

            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(),
                    16,
                    format.getChannels(),
                    format.getChannels() * 2,
                    format.getSampleRate(),
                    false
            );

            if (!format.matches(pcmFormat)) {
                audioStream = AudioSystem.getAudioInputStream(pcmFormat, audioStream);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            byte[] audioBytes = baos.toByteArray();
            audioStream.close();

            int numSamples = audioBytes.length / 2;
            short[] allSamples = new short[numSamples];
            ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer().get(allSamples);

            // 分离左右声道
            short[] leftChannel = new short[numSamples / 2];
            short[] rightChannel = new short[numSamples / 2];
            for (int i = 0; i < allSamples.length - 1; i += 2) {
                leftChannel[i / 2] = allSamples[i];
                rightChannel[i / 2] = allSamples[i + 1];
            }

            int w = audioProperties.getWaveformWidth();
            int h = audioProperties.getWaveformHeight() * 2 + 10; // 两倍高度加分隔

            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 背景
            g2d.setColor(new Color(30, 30, 30));
            g2d.fillRect(0, 0, w, h);

            // 左声道区域
            BufferedImage leftWave = drawWaveform(leftChannel, w, audioProperties.getWaveformHeight());
            g2d.drawImage(leftWave, 0, 0, null);

            // 分隔线
            g2d.setColor(new Color(80, 80, 80));
            g2d.setStroke(new BasicStroke(1));
            g2d.drawLine(0, audioProperties.getWaveformHeight() + 5, w, audioProperties.getWaveformHeight() + 5);

            // 右声道区域
            BufferedImage rightWave = drawWaveform(rightChannel, w, audioProperties.getWaveformHeight());
            g2d.drawImage(rightWave, 0, audioProperties.getWaveformHeight() + 10, null);

            g2d.dispose();
            javax.imageio.ImageIO.write(image, "PNG", waveformPath.toFile());

            log.info("立体声波形图生成成功: {}", waveformPath);
            return waveformPath.toString();

        } catch (UnsupportedAudioFileException e) {
            log.error("不支持的音频格式", e);
            throw new IOException("生成立体声波形图失败: " + e.getMessage(), e);
        }
    }
}
