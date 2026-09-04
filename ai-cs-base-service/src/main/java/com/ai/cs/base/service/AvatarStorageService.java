package com.ai.cs.base.service;

import com.ai.cs.common.exception.BusinessException;
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

@Service
public class AvatarStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Map<String, String> EXT_MAP = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );
    private static final long MAX_SIZE = 2 * 1024 * 1024;

    @Value("${app.upload-dir:uploads/avatars}")
    private String uploadDir;

    private Path storagePath;

    @PostConstruct
    public void init() throws IOException {
        storagePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(storagePath);
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的头像");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("头像不能超过 2MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("只支持 jpg、png、gif、webp 图片");
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + EXT_MAP.get(contentType);
        Path target = storagePath.resolve(filename).normalize();
        if (!target.startsWith(storagePath)) {
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
