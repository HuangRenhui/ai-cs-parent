package com.ai.cs.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 音频处理配置属性类
 */
@Data
@Component
@ConfigurationProperties(prefix = "audio")
public class AudioProperties {
    
    /**
     * 音频存储路径
     */
    private String storagePath = "./uploads/audios";
    
    /**
     * 最大文件大小（MB）
     */
    private int maxFileSize = 50;
    
    /**
     * 压缩阈值（KB），超过此大小自动压缩
     */
    private long compressThreshold = 5120;
    
    /**
     * 目标比特率（kbps），用于压缩
     */
    private int targetBitrate = 128;
    
    /**
     * 目标采样率（Hz），用于压缩
     */
    private int targetSampleRate = 44100;
    
    /**
     * 允许的音频格式
     */
    private String[] allowedFormats = {"mp3", "wav", "aac", "flac", "ogg", "wma", "m4a"};
    
    /**
     * 是否启用波形图生成
     */
    private boolean enableWaveform = true;
    
    /**
     * 波形图宽度（像素）
     */
    private int waveformWidth = 800;
    
    /**
     * 波形图高度（像素）
     */
    private int waveformHeight = 200;
    
    // ========== 向量化配置 ==========
    
    /**
     * 是否启用上传后自动向量化
     */
    private boolean autoVectorize = true;
    
    /**
     * 音频向量化集合名称（Chroma collection）
     */
    private String vectorCollection = "audio_embeddings";
    
    /**
     * 音频语义搜索返回的最大结果数
     */
    private int searchMaxResults = 5;
    
    /**
     * 音频语义搜索最低相似度阈值
     */
    private double searchMinScore = 0.5;
    
    /**
     * 是否启用音频转录（用于增强向量化效果）
     */
    private boolean enableTranscription = false;
    
    /**
     * 音频转录服务地址（Whisper等）
     */
    private String transcriptionUrl = "http://localhost:9000/transcribe";
}
