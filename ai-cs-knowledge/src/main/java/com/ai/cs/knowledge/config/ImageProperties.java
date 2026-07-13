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
}
