package com.ai.cs.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图片处理配置属性类
 */
@Data
@Component
@ConfigurationProperties(prefix = "image")
public class ImageProperties {
    
    /**
     * 图片存储路径
     */
    private String storagePath = "./uploads/images";
    
    /**
     * 缩略图存储路径
     */
    private String thumbnailPath = "./uploads/thumbnails";
    
    /**
     * 最大文件大小（MB）
     */
    private int maxFileSize = 10;
    
    /**
     * 压缩阈值（KB），超过此大小自动压缩
     */
    private long compressThreshold = 500;
    
    /**
     * 压缩质量（0.0-1.0）
     */
    private float compressQuality = 0.7f;
    
    /**
     * 缩略图宽度（像素）
     */
    private int thumbnailWidth = 200;
    
    /**
     * 缩略图高度（像素）
     */
    private int thumbnailHeight = 200;
    
    /**
     * 允许的图片格式
     */
    private String[] allowedFormats = {"jpg", "jpeg", "png", "gif", "webp", "bmp"};
    
    // ========== 向量化配置 ==========
    
    /**
     * 是否启用上传后自动向量化
     */
    private boolean autoVectorize = true;
    
    /**
     * 图片向量化集合名称（Chroma collection）
     */
    private String vectorCollection = "image_embeddings";
    
    /**
     * 图片语义搜索返回的最大结果数
     */
    private int searchMaxResults = 5;
    
    /**
     * 图片语义搜索最低相似度阈值
     */
    private double searchMinScore = 0.5;

    // ========== 对象存储与CDN配置 ==========

    /**
     * CDN加速域名（配置后图片URL使用CDN地址）
     */
    private String cdnDomain = "";

    /**
     * 对象存储类型: local, minio, oss, cos
     */
    private String storageType = "local";

    /**
     * 对象存储Endpoint
     */
    private String storageEndpoint = "";

    /**
     * 对象存储AccessKey
     */
    private String storageAccessKey = "";

    /**
     * 对象存储SecretKey
     */
    private String storageSecretKey = "";

    /**
     * 对象存储Bucket名称
     */
    private String storageBucket = "images";
}
