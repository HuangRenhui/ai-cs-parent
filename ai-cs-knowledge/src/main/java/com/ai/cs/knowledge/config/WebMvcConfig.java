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
        // 统一转绝对 file: URI：相对路径会随进程工作目录变化，jar 部署时极易映射到错误目录
        String rootPath = toLocation(fileProperties.getRootPath());

        // /files/** 映射到文件根存储目录
        registry.addResourceHandler("/files/**")
                .addResourceLocations(rootPath);

        // /uploads/** 也映射到同一目录（兼容旧路径）
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(rootPath);

        // 解压文件目录映射
        registry.addResourceHandler("/decompressed/**")
                .addResourceLocations(toLocation(fileProperties.getDecompressPath()));
    }

    /** 本地目录 → 以 / 结尾的绝对 file: URI */
    private String toLocation(String dir) {
        java.nio.file.Path path = java.nio.file.Paths.get(dir).toAbsolutePath().normalize();
        String location = path.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
