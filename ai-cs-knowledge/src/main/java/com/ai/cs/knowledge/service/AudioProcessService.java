package com.ai.cs.knowledge.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.knowledge.config.AudioProperties;
import com.ai.cs.knowledge.entity.AudioMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.info.MultimediaInfo;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * 音频处理服务
 * 提供音频上传、压缩、格式转换、时长提取、波形图生成等功能
 */
@Slf4j
@Service
public class AudioProcessService {

    private final AudioProperties audioProperties;
    private AudioVectorService audioVectorService;

    public AudioProcessService(AudioProperties audioProperties,
                                org.springframework.beans.factory.ObjectProvider<AudioVectorService> audioVectorServiceProvider) {
        this.audioProperties = audioProperties;
        this.audioVectorService = audioVectorServiceProvider.getIfAvailable();
        // 初始化存储目录
        initDirectories();
    }

    /**
     * 初始化存储目录
     */
    private void initDirectories() {
        try {
            Files.createDirectories(Paths.get(audioProperties.getStoragePath()));
            log.info("音频存储目录初始化完成: {}", audioProperties.getStoragePath());
        } catch (IOException e) {
            log.error("初始化存储目录失败", e);
            throw new BusinessException(500, "初始化存储目录失败: " + e.getMessage());
        }
    }

    /**
     * 上传音频并处理
     * @param file 上传的音频文件
     * @return 音频元数据
     */
    public AudioMetadata uploadAndProcess(MultipartFile file) throws IOException {
        // 1. 验证文件格式
        validateAudioFormat(file);
        
        // 2. 验证文件大小
        validateFileSize(file);
        
        // 3. 生成唯一文件ID
        String fileId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        
        // 4. 保存原始文件
        String storageFileName = fileId + "." + extension;
        Path storagePath = Paths.get(audioProperties.getStoragePath(), storageFileName);
        file.transferTo(storagePath.toFile());
        
        log.info("音频已保存: {}", storagePath);
        
        // 5. 提取元数据
        AudioMetadata metadata = extractMetadata(storagePath.toFile());
        metadata.setFileId(fileId);
        metadata.setOriginalFilename(originalFilename);
        metadata.setStoragePath(storagePath.toString());
        metadata.setFileSize(file.getSize());
        metadata.setUploadTime(LocalDateTime.now());
        
        // 6. 自动压缩（如果超过阈值）
        if (metadata.getFileSize() > audioProperties.getCompressThreshold() * 1024) {
            compressAudio(storagePath.toFile(), metadata);
        }
        
        // 7. 生成波形图（如果启用）
        if (audioProperties.isEnableWaveform()) {
            String waveformPath = generateWaveform(storagePath.toFile(), fileId);
            metadata.setWaveformPath(waveformPath);
        }
        
        // 8. 自动向量化入库（如果启用）
        if (audioProperties.isAutoVectorize() && audioVectorService != null) {
            try {
                audioVectorService.vectorize(metadata);
                log.info("音频自动向量化完成: fileId={}", fileId);
            } catch (Exception e) {
                log.warn("音频自动向量化失败（不影响上传）: fileId={}, 错误: {}", fileId, e.getMessage());
            }
        }
        
        return metadata;
    }

    /**
     * 验证音频格式
     */
    private void validateAudioFormat(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("无效的文件名");
        }
        
        String extension = getFileExtension(filename).toLowerCase();
        boolean allowed = Arrays.stream(audioProperties.getAllowedFormats())
                .anyMatch(format -> format.equalsIgnoreCase(extension));
        
        if (!allowed) {
            throw new IllegalArgumentException("不支持的音频格式: " + extension + 
                    ", 支持的格式: " + Arrays.toString(audioProperties.getAllowedFormats()));
        }
    }

    /**
     * 验证文件大小
     */
    private void validateFileSize(MultipartFile file) {
        long maxSizeBytes = audioProperties.getMaxFileSize() * 1024L * 1024L;
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("文件大小超过限制: " + 
                    (file.getSize() / 1024 / 1024) + "MB, 最大允许: " + 
                    audioProperties.getMaxFileSize() + "MB");
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf(".");
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }

    /**
     * 提取音频元数据
     */
    public AudioMetadata extractMetadata(File audioFile) {
        AudioMetadata metadata = new AudioMetadata();
        
        try {
            MultimediaObject multimediaObject = new MultimediaObject(audioFile);
            MultimediaInfo info = multimediaObject.getInfo();
            
            // 基本信息
            metadata.setDuration(info.getDuration() / 1000.0); // 转换为秒
            metadata.setBitrate(info.getAudio().getBitRate() / 1000); // 转换为kbps
            metadata.setSampleRate(info.getAudio().getSamplingRate());
            metadata.setChannels(info.getAudio().getChannels());
            
            // 文件格式
            String fileName = audioFile.getName();
            String extension = getFileExtension(fileName).toLowerCase();
            metadata.setFormat(extension);
            metadata.setMimeType(getMimeType(extension));
            
            // 编码器信息
            metadata.setEncoder(info.getAudio().getDecoder());
            
            // 标签信息（如果存在）
            java.util.Map<String, String> tags = info.getMetadata();
            if (tags != null && !tags.isEmpty()) {
                metadata.setTitle(tags.get("title"));
                metadata.setArtist(tags.get("artist"));
                metadata.setAlbum(tags.get("album"));
                String yearStr = tags.get("year");
                if (yearStr != null && !yearStr.isEmpty()) {
                    try {
                        metadata.setYear(Integer.parseInt(yearStr));
                    } catch (NumberFormatException e) {
                        // ignore invalid year format
                    }
                }
                metadata.setGenre(tags.get("genre"));
            }
            
            log.info("音频元数据提取完成: {} - 时长: {:.2f}s, 比特率: {}kbps, 采样率: {}Hz", 
                    audioFile.getName(), metadata.getDuration(), 
                    metadata.getBitrate(), metadata.getSampleRate());
                    
        } catch (Exception e) {
            log.error("提取音频元数据失败: {}", audioFile.getName(), e);
            throw new BusinessException(500, "提取音频元数据失败: " + e.getMessage());
        }
        
        return metadata;
    }

    /**
     * 压缩音频
     */
    public void compressAudio(File sourceFile, AudioMetadata metadata) {
        long originalSize = sourceFile.length();
        
        // 创建临时输出文件
        String tempFileName = UUID.randomUUID().toString() + "_compressed." + metadata.getFormat();
        File targetFile = new File(sourceFile.getParent(), tempFileName);
        
        try {
            // 配置编码参数
            AudioAttributes audioAttrs = new AudioAttributes();
            audioAttrs.setCodec(getCodecForFormat(metadata.getFormat()));
            audioAttrs.setBitRate(audioProperties.getTargetBitrate() * 1000); // 转换为bps
            audioAttrs.setSamplingRate(audioProperties.getTargetSampleRate());
            audioAttrs.setChannels(metadata.getChannels());
            
            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setAudioAttributes(audioAttrs);
            
            // 执行转码压缩
            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(sourceFile), targetFile, attrs);
            
            // 替换原文件
            if (targetFile.exists() && targetFile.length() > 0) {
                sourceFile.delete();
                targetFile.renameTo(sourceFile);
                
                long compressedSize = sourceFile.length();
                double ratio = (double) compressedSize / originalSize;
                
                metadata.setCompressed(true);
                metadata.setCompressedSize(compressedSize);
                metadata.setCompressionRatio(ratio);
                
                log.info("音频压缩完成: {} -> {} bytes, 压缩比: {:.2f}", 
                        originalSize, compressedSize, ratio);
            } else {
                log.warn("压缩后的文件无效，保留原文件");
                targetFile.delete();
            }
            
        } catch (Exception e) {
            log.error("音频压缩失败", e);
            if (targetFile.exists()) {
                targetFile.delete();
            }
            throw new BusinessException(500, "音频压缩失败: " + e.getMessage());
        }
    }

    /**
     * 音频格式转换
     * @param sourceFile 源文件
     * @param targetFormat 目标格式（mp3, wav, aac等）
     * @return 转换后的文件路径
     */
    public String convertFormat(File sourceFile, String targetFormat) {
        String fileId = UUID.randomUUID().toString();
        String targetFileName = fileId + "." + targetFormat.toLowerCase();
        Path targetPath = Paths.get(audioProperties.getStoragePath(), targetFileName);
        
        try {
            // 配置编码参数
            AudioAttributes audioAttrs = new AudioAttributes();
            audioAttrs.setCodec(getCodecForFormat(targetFormat));
            audioAttrs.setBitRate(audioProperties.getTargetBitrate() * 1000);
            audioAttrs.setSamplingRate(audioProperties.getTargetSampleRate());
            
            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setAudioAttributes(audioAttrs);
            
            // 执行转码
            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(sourceFile), targetPath.toFile(), attrs);
            
            log.info("音频格式转换完成: {} -> {}", sourceFile.getName(), targetPath);
            return targetPath.toString();
            
        } catch (Exception e) {
            log.error("音频格式转换失败", e);
            throw new BusinessException(500, "音频格式转换失败: " + e.getMessage());
        }
    }

    /**
     * 调整音频比特率
     * @param sourceFile 源文件
     * @param bitrate 目标比特率（kbps）
     * @return 调整后的文件路径
     */
    public String adjustBitrate(File sourceFile, int bitrate) {
        String fileId = UUID.randomUUID().toString();
        String extension = getFileExtension(sourceFile.getName());
        String outputFileName = fileId + "_bitrate." + extension;
        Path outputPath = Paths.get(audioProperties.getStoragePath(), outputFileName);
        
        try {
            AudioAttributes audioAttrs = new AudioAttributes();
            audioAttrs.setCodec(getCodecForFormat(extension));
            audioAttrs.setBitRate(bitrate * 1000);
            
            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setAudioAttributes(audioAttrs);
            
            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(sourceFile), outputPath.toFile(), attrs);
            
            log.info("音频比特率调整完成: {}kbps -> {}", bitrate, outputPath);
            return outputPath.toString();
            
        } catch (Exception e) {
            log.error("音频比特率调整失败", e);
            throw new BusinessException(500, "音频比特率调整失败: " + e.getMessage());
        }
    }

    /**
     * 生成音频波形图
     * @param audioFile 音频文件
     * @param fileId 文件ID
     * @return 波形图路径
     */
    public String generateWaveform(File audioFile, String fileId) throws IOException {
        String waveformFileName = fileId + "_waveform.png";
        Path waveformPath = Paths.get(audioProperties.getStoragePath(), waveformFileName);
        
        try {
            // 使用FFmpeg提取音频数据并生成波形图
            // 这里简化实现：创建一个占位波形图
            BufferedImage waveformImage = createPlaceholderWaveform(
                    audioProperties.getWaveformWidth(), 
                    audioProperties.getWaveformHeight());
            
            ImageIO.write(waveformImage, "PNG", waveformPath.toFile());
            
            log.info("波形图已生成: {}", waveformPath);
            return waveformPath.toString();
            
        } catch (Exception e) {
            log.error("生成波形图失败", e);
            return null;
        }
    }

    /**
     * 创建占位波形图（实际项目中应使用FFmpeg分析真实音频数据）
     */
    private BufferedImage createPlaceholderWaveform(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        // 设置抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 背景色
        g2d.setColor(new Color(240, 240, 240));
        g2d.fillRect(0, 0, width, height);
        
        // 绘制模拟波形
        g2d.setColor(new Color(66, 133, 244));
        int centerY = height / 2;
        for (int x = 0; x < width; x++) {
            // 生成随机振幅模拟波形
            int amplitude = (int) (Math.random() * height * 0.4);
            int y1 = centerY - amplitude;
            int y2 = centerY + amplitude;
            g2d.drawLine(x, y1, x, y2);
        }
        
        g2d.dispose();
        return image;
    }

    /**
     * 根据格式获取对应的编解码器
     */
    private String getCodecForFormat(String format) {
        return switch (format.toLowerCase()) {
            case "mp3" -> "libmp3lame";
            case "wav" -> "pcm_s16le";
            case "aac" -> "aac";
            case "flac" -> "flac";
            case "ogg" -> "libvorbis";
            case "wma" -> "wmav2";
            case "m4a" -> "aac";
            default -> "libmp3lame";
        };
    }

    /**
     * 根据扩展名获取MIME类型
     */
    private String getMimeType(String extension) {
        return switch (extension.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "aac" -> "audio/aac";
            case "flac" -> "audio/flac";
            case "ogg" -> "audio/ogg";
            case "wma" -> "audio/x-ms-wma";
            case "m4a" -> "audio/mp4";
            default -> "application/octet-stream";
        };
    }

    /**
     * 删除音频文件
     */
    public void deleteAudio(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
            log.info("音频文件已删除: {}", filePath);
        } catch (IOException e) {
            log.error("删除音频文件失败: {}", filePath, e);
        }
    }
}