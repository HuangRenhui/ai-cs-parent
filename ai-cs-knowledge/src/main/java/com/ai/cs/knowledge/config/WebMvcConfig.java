package com.ai.cs.knowledge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置
 * 配置静态资源映射，让上传的文件可以通过URL直接预览
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final FileProperties fileProperties;

    public WebMvcConfig(FileProperties fileProperties) {
        this.fileProperties = fileProperties;
    }

    /**
     * 注册静态资源映射
     * 将磁盘上的上传目录暴露为HTTP可访问路径，供前端直接预览/下载文件
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射上传文件根目录
        String rootPath = fileProperties.getRootPath();
        if (!rootPath.endsWith("/")) {
            rootPath += "/";
        }

        // /files/** 映射到文件根存储目录
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + rootPath);

        // /uploads/** 也映射到同一目录（兼容旧路径）
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + rootPath);

        // 解压文件目录映射
        String decompressPath = fileProperties.getDecompressPath();
        if (!decompressPath.endsWith("/")) {
            decompressPath += "/";
        }
        registry.addResourceHandler("/decompressed/**")
                .addResourceLocations("file:" + decompressPath);
    }
}
