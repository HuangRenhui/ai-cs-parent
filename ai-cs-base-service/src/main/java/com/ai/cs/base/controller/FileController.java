package com.ai.cs.base.controller;

import com.ai.cs.base.service.AvatarStorageService;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.util.Set;

/**
 * 文件上传控制器
 *
 * @author huangrenhui
 */
@RestController
@RequestMapping("/file")
public class FileController {

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp");

    @Resource
    private AvatarStorageService avatarStorageService;

    /**
     * 上传头像，返回可访问的 URL 路径
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            return Result.fail(400, "文件大小不能超过 2MB");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !ALLOWED_AVATAR_EXTENSIONS.stream().anyMatch(ext -> originalFilename.toLowerCase().endsWith(ext))) {
            return Result.fail(400, "不支持的文件类型，仅允许: " + ALLOWED_AVATAR_EXTENSIONS);
        }
        return Result.success("上传成功", avatarStorageService.save(file));
    }
}