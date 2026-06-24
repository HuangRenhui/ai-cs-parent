package com.ai.cs.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传配置属性类
 */
@Data
@Component
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {
    private String tempPath;
}
