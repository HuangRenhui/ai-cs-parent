package com.ai.cs.base.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源映射配置：把头像上传目录暴露为 /files/avatars/** 可访问的 URL
 *
 * @author huangrenhui
 */
@Configuration
public class FileWebConfig implements WebMvcConfigurer {

    /** 头像上传目录（默认 uploads/avatars） */
    @Value("${app.upload-dir:uploads/avatars}")
    private String uploadDir;

    /**
     * 注册资源处理器：将 /files/avatars/** 映射到本地上传目录
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 转成绝对路径的 file: URI，保证任意工作目录下都能定位
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        String location = dir.toUri().toString();
        // 资源位置必须以 / 结尾，否则子路径拼接会出错
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/files/avatars/**")
                .addResourceLocations(location);
    }
}
