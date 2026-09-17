package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.entity.DocumentVersion;
import com.ai.cs.knowledge.service.DocumentVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文档版本管理控制器
 * 提供文档版本创建、查询、回退、对比等功能
 * 
 * @author huangrenhui
 * @date 2026/6/24
 */
@RestController
@RequestMapping("/api/document/version")
@RequiredArgsConstructor
@Tag(name = "文档版本管理", description = "文档版本创建、查询、回退、对比等功能")
public class DocumentVersionController {
    
    private final DocumentVersionService documentVersionService;
    
    /**
     * 获取所有文档列表（当前版本）
     * @return 文档列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取所有文档列表", description = "获取所有文档的当前版本列表")
    public Result<List<DocumentVersion>> getAllDocuments() {
        try {
            List<DocumentVersion> documents = documentVersionService.getAllDocuments();
            return Result.success(documents);
        } catch (Exception e) {
            return Result.fail("获取文档列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取指定文档的所有版本
     * @param documentId 文档ID
     * @return 版本列表
     */
    @GetMapping("/versions/{documentId}")
    @Operation(summary = "获取文档的所有版本", description = "根据文档ID查询所有历史版本")
    public Result<List<DocumentVersion>> getVersionsByDocumentId(
            @Parameter(description = "文档ID") @PathVariable String documentId) {
        try {
            List<DocumentVersion> versions = documentVersionService.getVersionsByDocumentId(documentId);
            return Result.success(versions);
        } catch (Exception e) {
            return Result.fail("获取版本列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取文档的当前版本
     * @param documentId 文档ID
     * @return 当前版本
     */
    @GetMapping("/current/{documentId}")
    @Operation(summary = "获取文档当前版本", description = "获取指定文档的当前激活版本")
    public Result<DocumentVersion> getCurrentVersion(
            @Parameter(description = "文档ID") @PathVariable String documentId) {
        try {
            DocumentVersion version = documentVersionService.getCurrentVersion(documentId);
            if (version == null) {
                return Result.fail("当前版本不存在");
            }
            return Result.success(version);
        } catch (Exception e) {
            return Result.fail("获取当前版本失败: " + e.getMessage());
        }
    }
    
    /**
     * 回退到指定版本
     * @param documentId 文档ID
     * @param targetVersion 目标版本号
     * @return 操作结果
     */
    @PostMapping("/rollback")
    @Operation(summary = "回退到指定版本", description = "将文档回退到指定的历史版本")
    public Result<String> rollbackToVersion(
            @Parameter(description = "文档ID") @RequestParam String documentId,
            @Parameter(description = "目标版本号") @RequestParam Integer targetVersion) {
        try {
            String result = documentVersionService.rollbackToVersion(documentId, targetVersion);
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail("版本回退失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除指定版本
     * @param id 版本ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除指定版本", description = "删除指定的文档版本（逻辑删除）")
    public Result<String> deleteVersion(
            @Parameter(description = "版本ID") @PathVariable Long id) {
        try {
            String result = documentVersionService.deleteVersion(id);
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail("删除版本失败: " + e.getMessage());
        }
    }
    
    /**
     * 比较两个版本
     * @param version1Id 版本1 ID
     * @param version2Id 版本2 ID
     * @return 比较结果
     */
    @GetMapping("/compare")
    @Operation(summary = "比较两个版本", description = "比较两个文档版本的差异")
    public Result<String> compareVersions(
            @Parameter(description = "版本1 ID") @RequestParam Long version1Id,
            @Parameter(description = "版本2 ID") @RequestParam Long version2Id) {
        try {
            String result = documentVersionService.compareVersions(version1Id, version2Id);
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail("版本比较失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查文件是否已存在（通过MD5）
     * @param fileMd5 文件MD5
     * @return 是否存在
     */
    @GetMapping("/exists")
    @Operation(summary = "检查文件是否存在", description = "通过MD5检查文件是否已存在")
    public Result<Boolean> existsByMd5(
            @Parameter(description = "文件MD5") @RequestParam String fileMd5) {
        try {
            boolean exists = documentVersionService.existsByMd5(fileMd5);
            return Result.success(exists);
        } catch (Exception e) {
            return Result.fail("检查文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取版本详情
     * @param id 版本ID
     * @return 版本详情
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "获取版本详情", description = "根据版本ID获取详细信息")
    public Result<DocumentVersion> getVersionDetail(
            @Parameter(description = "版本ID") @PathVariable Long id) {
        try {
            DocumentVersion version = documentVersionService.getById(id);
            if (version == null) {
                return Result.fail("版本不存在");
            }
            return Result.success(version);
        } catch (Exception e) {
            return Result.fail("获取版本详情失败: " + e.getMessage());
        }
    }
}
