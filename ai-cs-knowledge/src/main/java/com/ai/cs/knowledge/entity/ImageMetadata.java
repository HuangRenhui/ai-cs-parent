package com.ai.cs.knowledge.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图片元数据实体类
 * 包含图片的基本信息和EXIF信息
 */
@Data
public class ImageMetadata {
    
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
     * 缩略图路径
     */
    private String thumbnailPath;
    
    /**
     * 文件大小（字节）
     */
    private long fileSize;
    
    /**
     * 压缩后文件大小（字节）
     */
    private long compressedSize;
    
    /**
     * 图片宽度（像素）
     */
    private int width;
    
    /**
     * 图片高度（像素）
     */
    private int height;
    
    /**
     * 图片格式（jpg, png, gif等）
     */
    private String format;
    
    /**
     * MIME类型
     */
    private String mimeType;
    
    // ========== EXIF 信息 ==========
    
    /**
     * 相机制造商
     */
    private String cameraMake;
    
    /**
     * 相机型号
     */
    private String cameraModel;
    
    /**
     * 拍摄时间
     */
    private LocalDateTime dateTimeOriginal;
    
    /**
     * 光圈值
     */
    private String aperture;
    
    /**
     * 快门速度
     */
    private String shutterSpeed;
    
    /**
     * ISO感光度
     */
    private Integer iso;
    
    /**
     * 焦距（mm）
     */
    private Double focalLength;
    
    /**
     * GPS纬度
     */
    private Double gpsLatitude;
    
    /**
     * GPS经度
     */
    private Double gpsLongitude;
    
    /**
     * 图片描述
     */
    private String description;
    
    /**
     * 版权信息
     */
    private String copyright;
    
    /**
     * 软件信息
     */
    private String software;
    
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
}
