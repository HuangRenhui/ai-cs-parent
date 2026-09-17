package com.ai.cs.knowledge.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.util.FileMagicValidator;
import com.ai.cs.knowledge.config.AudioProperties;
import com.ai.cs.knowledge.entity.AudioMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * 音频处理服务（占位）
 *
 * <p>TODO 后续实现：经 jave/FFmpeg 解析音频时长与码率、按阈值压缩、格式转换、
 * 比特率调整、抽取采样并绘制波形图（见 {@code AudioWaveformService}），
 * 以及上传后按配置自动向量化入库。</p>
 *
 * <p>当前保留：格式与魔数校验、文件大小校验、原始文件落盘、按扩展名映射格式/MIME、
 * 文件删除与存储目录初始化（本地 IO 与安全校验）。</p>
 *
 * <p>当前占位：{@link #extractMetadata}、{@link #compressAudio}、{@link #convertFormat}、
 * {@link #adjustBitrate} 抛 {@code BusinessException(503)}；{@link #generateWaveform} 抛
 * {@code IOException}（与服务不可用语义一致）。{@link #uploadAndProcess} 仍会真正落盘，
 * 但压缩、波形图与自动向量化均不再执行。</p>
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
     * 上传音频并保存（占位：仅校验与落盘，不做压缩/波形图/向量化）
     *
     * @param file 上传的音频文件
     * @return 音频元数据（仅含文件级信息）
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

        // 5. 组装文件级元数据（时长/码率解析、压缩、波形图、自动向量化均未接入）
        AudioMetadata metadata = new AudioMetadata();
        metadata.setFileId(fileId);
        metadata.setOriginalFilename(originalFilename);
        metadata.setStoragePath(storagePath.toString());
        metadata.setFileSize(file.getSize());
        metadata.setFormat(extension.toLowerCase());
        metadata.setMimeType(getMimeType(extension));
        metadata.setUploadTime(LocalDateTime.now());

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
        // 能识别魔数的格式严格校验；m4a/aac 等容器头不稳定时按扩展名放行
        try {
            FileMagicValidator.assertAllowed(file, audioProperties.getAllowedFormats());
        } catch (BusinessException ex) {
            if ("mp3".equals(extension) || "flac".equals(extension) || "ogg".equals(extension)
                    || "wav".equals(extension)) {
                throw new IllegalArgumentException(ex.getMessage());
            }
            log.warn("音频魔数未识别，已按扩展名放行 ext={}: {}", extension, ex.getMessage());
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
     * 提取音频元数据（占位：不解析媒体，抛 503）
     *
     * @param audioFile 音频文件
     * @return 不返回（抛异常）
     */
    public AudioMetadata extractMetadata(File audioFile) {
        log.warn("[占位] 音频元数据提取未实现 file={}", audioFile == null ? null : audioFile.getName());
        throw new BusinessException(503, "音频元数据提取为占位实现，后端未接入媒体解析");
    }

    /**
     * 压缩音频（占位：不转码，抛 503）
     *
     * @param sourceFile 源文件
     * @param metadata   音频元数据
     */
    public void compressAudio(File sourceFile, AudioMetadata metadata) {
        log.warn("[占位] 音频压缩未实现 file={}", sourceFile == null ? null : sourceFile.getName());
        throw new BusinessException(503, "音频压缩为占位实现，后端未接入 FFmpeg 转码");
    }

    /**
     * 音频格式转换（占位：不转码，抛 503）
     *
     * @param sourceFile   源文件
     * @param targetFormat 目标格式（mp3, wav, aac等）
     * @return 不返回（抛异常）
     */
    public String convertFormat(File sourceFile, String targetFormat) {
        log.warn("[占位] 音频格式转换未实现 targetFormat={}", targetFormat);
        throw new BusinessException(503, "音频格式转换为占位实现，后端未接入 FFmpeg 转码");
    }

    /**
     * 调整音频比特率（占位：不转码，抛 503）
     *
     * @param sourceFile 源文件
     * @param bitrate    目标比特率（kbps）
     * @return 不返回（抛异常）
     */
    public String adjustBitrate(File sourceFile, int bitrate) {
        log.warn("[占位] 音频比特率调整未实现 bitrate={}", bitrate);
        throw new BusinessException(503, "音频比特率调整为占位实现，后端未接入 FFmpeg 转码");
    }

    /**
     * 生成音频波形图（占位：不抽取采样，抛 IOException）
     *
     * @param audioFile 音频文件
     * @param fileId    文件ID
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public String generateWaveform(File audioFile, String fileId) throws IOException {
        log.warn("[占位] 波形图生成未实现 fileId={}", fileId);
        throw new IOException("波形图生成为占位实现，后端未接入音频采样绘制");
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
