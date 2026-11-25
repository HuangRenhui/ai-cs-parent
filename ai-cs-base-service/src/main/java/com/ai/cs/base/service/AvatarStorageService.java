package com.ai.cs.base.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.util.FileMagicValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 头像文件存储服务：负责头像上传的校验与落盘
 *
 * @author huangrenhui
 */
@Slf4j
@Service
public class AvatarStorageService {

    /** 允许上传的图片 MIME 类型白名单 */
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    /** MIME 类型到文件扩展名的映射 */
    private static final Map<String, String> EXT_MAP = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );
    /** 头像大小上限：2MB */
    private static final long MAX_SIZE = 2 * 1024 * 1024;

    /** 上传目录（默认 uploads/avatars） */
    @Value("${app.upload-dir:uploads/avatars}")
    private String uploadDir;

    /** 上传目录的绝对路径（懒初始化，需 volatile 保证可见性） */
    private volatile Path storagePath;

    /**
     * 启动初始化：目录不可创建时不阻断服务启动，首次上传时重试
     */
    @PostConstruct
    public void init() {
        try {
            prepareStorage();
        } catch (Exception e) {
            log.error("头像目录初始化失败，将在首次上传时重试: {}", e.getMessage());
        }
    }

    /** 懒初始化上传目录 */
    private Path prepareStorage() throws IOException {
        Path path = storagePath;
        if (path == null) {
            synchronized (this) {
                if (storagePath == null) {
                    storagePath = Paths.get(uploadDir).toAbsolutePath().normalize();
                    Files.createDirectories(storagePath);
                }
                path = storagePath;
            }
        }
        return path;
    }

    /**
     * 保存头像文件，返回可访问的 URL 路径
     *
     * @param file 上传的头像文件
     * @return 头像访问路径（/files/avatars/xxx）
     */
    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的头像");
        }
        // 大小限制：防止超大文件拖垮磁盘
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("头像不能超过 2MB");
        }
        // 魔数校验：不以 Content-Type 为准，防止伪造 MIME 上传可执行文件
        FileMagicValidator.assertAllowed(file, "jpg", "jpeg", "png", "gif", "webp");
        // 扩展名只从 MIME 白名单推导，绝不采信原始文件名：
        // 否则「PNG 魔数 + .html 后缀」会被同源静态托管成存储型 XSS
        String ext = EXT_MAP.get(file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT));
        if (ext == null) {
            throw new BusinessException("头像格式不支持，仅支持 jpg/png/gif/webp");
        }
        // UUID 生成随机文件名，避免文件名冲突与路径注入
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Path storage;
        try {
            storage = prepareStorage();
        } catch (IOException e) {
            throw new BusinessException("头像目录不可用，请检查挂载与权限");
        }
        Path target = storage.resolve(filename).normalize();
        // 防御目录穿越：解析后的路径必须仍位于存储目录内
        if (!target.startsWith(storage)) {
            throw new BusinessException("非法文件路径");
        }
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new BusinessException("头像保存失败，请稍后重试");
        }
        return "/files/avatars/" + filename;
    }
}
