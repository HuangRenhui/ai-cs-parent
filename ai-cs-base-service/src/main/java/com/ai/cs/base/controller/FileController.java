package com.ai.cs.base.controller;

import com.ai.cs.base.service.AvatarStorageService;
import com.ai.cs.common.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

/**
 * 文件上传控制器
 *
 * @author huangrenhui
 */
@RestController
@RequestMapping("/file")
public class FileController {

    @Resource
    private AvatarStorageService avatarStorageService;

    /**
     * 上传头像，返回可访问的 URL 路径
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success("上传成功", avatarStorageService.save(file));
    }
}
