package com.ai.cs.knowledge.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 音频元数据实体类
 * 包含音频文件的基本信息和处理信息
 */
@Data
public class AudioMetadata {
    
    /**
     * 文件ID
     */
    private String fileId;
    
    /**
     * 原始文件名
     */
    private String originalFilename;
    
    /**
     * 存储路径
     */
    private String storagePath;
    
    /**
     * 文件大小（字节）
     */
    private long fileSize;
    
    /**
     * 压缩后文件大小（字节）
     */
    private long compressedSize;
    
    /**
     * 音频格式（mp3, wav, aac等）
     */
    private String format;
    
    /**
     * MIME类型
     */
    private String mimeType;
    
    // ========== 音频属性信息 ==========
    
    /**
     * 音频时长（秒）
     */
    private double duration;
    
    /**
     * 比特率（kbps）
     */
    private int bitrate;
    
    /**
     * 采样率（Hz）
     */
    private int sampleRate;
    
    /**
     * 声道数（1=单声道，2=立体声）
     */
    private int channels;
    
    /**
     * 编码器名称
     */
    private String encoder;
    
    /**
     * 艺术家/演唱者
     */
    private String artist;
    
    /**
     * 专辑名称
     */
    private String album;
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 年份
     */
    private Integer year;
    
    /**
     * 流派
     */
    private String genre;
    
    /**
     * 波形图路径
     */
    private String waveformPath;
    
    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;
    
    /**
     * 是否已压缩
     */
    private boolean compressed;
    
    /**
     * 压缩比例
     */
    private double compressionRatio;
    
    // ========== 向量化相关字段 ==========
    
    /**
     * 向量库中的记录ID
     */
    private String vectorId;
    
    /**
     * 是否已向量化
     */
    private boolean vectorized;
    
    /**
     * 向量库集合名称
     */
    private String vectorCollection;
    
    /**
     * 音频转录文本（用于向量化检索）
     */
    private String transcription;
    
    /**
     * 用于向量化的描述文本（基于元数据自动生成）
     */
    private String vectorDescription;
}
