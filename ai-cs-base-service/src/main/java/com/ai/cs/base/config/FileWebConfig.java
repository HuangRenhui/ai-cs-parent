package com.ai.cs.base.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源映射配置：把上传目录暴露为可访问的 URL。
 *
 * <p>当前暴露两类资源：</p>
 * <ul>
 *   <li>{@code /files/avatars/**} —— 用户头像</li>
 *   <li>{@code /files/chat/**} —— C 端对话附件（图片直接展示，文档走下载）</li>
 * </ul>
 *
 * @author huangrenhui
 */
@Configuration
public class FileWebConfig implements WebMvcConfigurer {

    /** 头像上传目录（默认 uploads/avatars） */
    @Value("${app.upload-dir:uploads/avatars}")
    private String avatarDir;

    /** 对话附件上传根目录（默认 uploads/chat） */
    @Value("${app.chat-upload-dir:uploads/chat}")
    private String chatDir;

    /**
     * 注册资源处理器：将两类上传目录映射为静态资源路径
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/avatars/**")
                .addResourceLocations(toLocation(avatarDir));
        registry.addResourceHandler("/files/chat/**")
                .addResourceLocations(toLocation(chatDir));
    }

    /**
     * 把本地目录转换为可用的资源位置 URI。
     * <p>资源位置必须以 / 结尾，否则子路径拼接会出错。</p>
     */
    private String toLocation(String dir) {
        // 转成绝对路径的 file: URI，保证任意工作目录下都能定位
        Path path = Paths.get(dir).toAbsolutePath().normalize();
        String location = path.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        return location;
    }
}
