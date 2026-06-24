package com.ai.cs.knowledge.controller;

import com.ai.cs.common.result.Result;
import com.ai.cs.knowledge.service.DocumentLoadService;
import com.ai.cs.knowledge.service.FileUploadService;
import com.ai.cs.knowledge.service.RagChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * RAG知识库接口控制器
 * 提供基于LangChain4j的智能问答、文档上传入库等功能
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Tag(name = "RAG知识库接口", description = "基于LangChain4j+Ollama+Chroma的智能问答系统")
public class RagController {

    private final RagChatService ragChatService;
    private final DocumentLoadService documentLoadService;
    private final FileUploadService fileUploadService;

    /**
     * 多用户问答接口，userId区分会话
     * @param userId 用户唯一ID
     * @param question 用户提问内容
     * @return AI回答
     */
    @PostMapping("/chat")
    @Operation(summary = "用户对话问答（带独立会话记忆）", description = "根据userId隔离不同用户的对话历史，实现多轮对话")
    public Result<String> chat(
            @Parameter(description = "用户唯一ID") @RequestParam String userId,
            @Parameter(description = "用户提问内容") @RequestParam String question
    ) {
        try {
            String answer = ragChatService.chat(userId, question);
            return Result.success(answer);
        } catch (Exception e) {
            return Result.fail("问答失败: " + e.getMessage());
        }
    }

    /**
     * 上传PDF并导入向量库
     * @param file PDF文件
     * @return 处理结果
     */
    @PostMapping("/upload/pdf")
    @Operation(summary = "上传PDF文档入库向量库", description = "解析PDF文件，切片并向量化存入Chroma向量库")
    public Result<String> uploadPdf(@RequestParam MultipartFile file) {
        try {
            String tempPath = fileUploadService.saveTempFile(file);
            String res = documentLoadService.loadPdf(tempPath);
            fileUploadService.deleteTempFile(tempPath);
            return Result.success(res);
        } catch (Exception e) {
            return Result.fail("文件解析失败：" + e.getMessage());
        }
    }

    /**
     * 上传普通文件(txt/docx/md)入库
     * @param file 文本文件
     * @return 处理结果
     */
    @PostMapping("/upload/file")
    @Operation(summary = "上传普通文件入库", description = "支持txt、docx、md等格式的文件解析和向量化")
    public Result<String> uploadFile(@RequestParam MultipartFile file) {
        try {
            String tempPath = fileUploadService.saveTempFile(file);
            String res = documentLoadService.loadCommonFile(tempPath);
            fileUploadService.deleteTempFile(tempPath);
            return Result.success(res);
        } catch (Exception e) {
            return Result.fail("文件解析失败：" + e.getMessage());
        }
    }

    /**
     * 清空单个用户对话记忆
     * @param userId 用户ID
     * @return 操作结果
     */
    @PostMapping("/memory/clear/user")
    @Operation(summary = "清空单个用户对话记忆", description = "清除指定用户的历史对话记录")
    public Result<String> clearUserMemory(@RequestParam String userId) {
        try {
            String msg = ragChatService.clearUserChatMemory(userId);
            return Result.success(msg);
        } catch (Exception e) {
            return Result.fail("清空记忆失败: " + e.getMessage());
        }
    }

    /**
     * 清空全部会话记忆
     * @return 操作结果
     */
    @PostMapping("/memory/clear/all")
    @Operation(summary = "清空全部用户对话记忆", description = "清除所有用户的历史对话记录")
    public Result<String> clearAllMemory() {
        try {
            String msg = ragChatService.clearAllChatMemory();
            return Result.success(msg);
        } catch (Exception e) {
            return Result.fail("清空记忆失败: " + e.getMessage());
        }
    }

    /**
     * 上传PDF并导入向量库（带版本管理）
     * @param file PDF文件
     * @param documentId 文档ID
     * @param documentName 文档名称
     * @param versionDescription 版本描述
     * @param uploaderId 上传人ID
     * @param uploaderName 上传人姓名
     * @return 处理结果
     */
    @PostMapping("/upload/pdf/versioned")
    @Operation(summary = "上传PDF文档入库向量库（带版本管理）", description = "解析PDF文件，切片并向量化存入Chroma向量库，同时创建版本记录")
    public Result<String> uploadPdfWithVersion(
            @RequestParam MultipartFile file,
            @Parameter(description = "文档ID") @RequestParam String documentId,
            @Parameter(description = "文档名称") @RequestParam String documentName,
            @Parameter(description = "版本描述") @RequestParam(required = false) String versionDescription,
            @Parameter(description = "上传人ID") @RequestParam(required = false) Long uploaderId,
            @Parameter(description = "上传人姓名") @RequestParam(required = false) String uploaderName) {
        try {
            String tempPath = fileUploadService.saveTempFile(file);
            String res = documentLoadService.loadPdf(tempPath, documentId, documentName, versionDescription, uploaderId, uploaderName);
            fileUploadService.deleteTempFile(tempPath);
            return Result.success(res);
        } catch (Exception e) {
            return Result.fail("文件解析失败：" + e.getMessage());
        }
    }

    /**
     * 上传普通文件入库（带版本管理）
     * @param file 文本文件
     * @param documentId 文档ID
     * @param documentName 文档名称
     * @param versionDescription 版本描述
     * @param uploaderId 上传人ID
     * @param uploaderName 上传人姓名
     * @return 处理结果
     */
    @PostMapping("/upload/file/versioned")
    @Operation(summary = "上传普通文件入库（带版本管理）", description = "支持txt、docx、md等格式的文件解析和向量化，同时创建版本记录")
    public Result<String> uploadFileWithVersion(
            @RequestParam MultipartFile file,
            @Parameter(description = "文档ID") @RequestParam String documentId,
            @Parameter(description = "文档名称") @RequestParam String documentName,
            @Parameter(description = "版本描述") @RequestParam(required = false) String versionDescription,
            @Parameter(description = "上传人ID") @RequestParam(required = false) Long uploaderId,
            @Parameter(description = "上传人姓名") @RequestParam(required = false) String uploaderName) {
        try {
            String tempPath = fileUploadService.saveTempFile(file);
            String res = documentLoadService.loadCommonFile(tempPath, documentId, documentName, versionDescription, uploaderId, uploaderName);
            fileUploadService.deleteTempFile(tempPath);
            return Result.success(res);
        } catch (Exception e) {
            return Result.fail("文件解析失败：" + e.getMessage());
        }
    }
}
