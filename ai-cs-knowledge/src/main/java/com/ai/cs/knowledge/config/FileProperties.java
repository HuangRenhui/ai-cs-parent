package com.ai.cs.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件管理配置属性
 * 涵盖文件查看、下载、解压等功能
 */
@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    /**
     * 文件根存储路径
     */
    private String rootPath = "./uploads";

    /**
     * 解压文件输出目录
     */
    private String decompressPath = "./uploads/decompressed";

    /**
     * 批量打包临时目录
     */
    private String packagePath = "./uploads/packages";

    /**
     * 单个文件最大下载大小（MB）
     */
    private long maxDownloadSize = 100;

    /**
     * 压缩包最大解压大小（MB），防止Zip炸弹
     */
    private long maxDecompressSize = 500;

    /**
     * 最大解压文件数量，防止Zip炸弹
     */
    private int maxDecompressFiles = 1000;

    /**
     * 允许预览的文件MIME类型前缀
     */
    private String[] previewableTypes = {"image/", "audio/", "video/", "application/pdf"};

    /**
     * 允许解压的压缩格式
     */
    private String[] allowedArchiveFormats = {"zip", "7z", "tar", "gz", "bz2", "tgz"};

    /**
     * 是否启用文件访问Token验证
     */
    private boolean enableAccessControl = false;

    /**
     * 文件访问Token过期时间（秒）
     */
    private long tokenExpireSeconds = 3600;

    /**
     * 断点续传分片大小（字节，默认1MB）
     */
    private long chunkSize = 1048576;
}
