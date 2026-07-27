package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.RagProperties;
import com.ai.cs.knowledge.entity.DocumentVersion;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文档加载服务
 * 负责将PDF、TXT、Word等格式的文档解析、切片并向量化存入Chroma向量库
 * 集成文档版本管理功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLoadService {

    private final EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final RagProperties ragProperties;
    private final DocumentVersionService documentVersionService;
    private final SemanticSplitService semanticSplitService;

    /**
     * 加载PDF文件入库
     * @param filePath PDF文件路径
     * @return 处理结果信息
     */
    public String loadPdf(String filePath) {
        DocumentParser parser = new ApachePdfBoxDocumentParser();
        return loadFile(filePath, parser, null, null, null, null, null);
    }

    /**
     * 加载PDF文件入库（带版本管理）
     * @param filePath PDF文件路径
     * @param documentId 文档ID
     * @param documentName 文档名称
     * @param versionDescription 版本描述
     * @param uploaderId 上传人ID
     * @param uploaderName 上传人姓名
     * @return 处理结果信息
     */
    public String loadPdf(String filePath, String documentId, String documentName, 
                         String versionDescription, Long uploaderId, String uploaderName) {
        DocumentParser parser = new ApachePdfBoxDocumentParser();
        return loadFile(filePath, parser, documentId, documentName, versionDescription, uploaderId, uploaderName);
    }

    /**
     * 加载txt、docx、md等通用文件
     * @param filePath 文件路径
     * @return 处理结果信息
     */
    public String loadCommonFile(String filePath) {
        DocumentParser parser = new ApacheTikaDocumentParser();
        return loadFile(filePath, parser, null, null, null, null, null);
    }

    /**
     * 加载txt、docx、md等通用文件（带版本管理）
     * @param filePath 文件路径
     * @param documentId 文档ID
     * @param documentName 文档名称
     * @param versionDescription 版本描述
     * @param uploaderId 上传人ID
     * @param uploaderName 上传人姓名
     * @return 处理结果信息
     */
    public String loadCommonFile(String filePath, String documentId, String documentName, 
                                 String versionDescription, Long uploaderId, String uploaderName) {
        DocumentParser parser = new ApacheTikaDocumentParser();
        return loadFile(filePath, parser, documentId, documentName, versionDescription, uploaderId, uploaderName);
    }

    /**
     * 通用文件加载方法
     * @param filePath 文件路径
     * @param parser 文档解析器
     * @param documentId 文档ID（可选，为空时不创建版本记录）
     * @param documentName 文档名称（可选）
     * @param versionDescription 版本描述（可选）
     * @param uploaderId 上传人ID（可选）
     * @param uploaderName 上传人姓名（可选）
     * @return 处理结果信息
     */
    private String loadFile(String filePath, DocumentParser parser, String documentId, 
                           String documentName, String versionDescription, 
                           Long uploaderId, String uploaderName) {
        try {
            Path path = Paths.get(filePath);
            Document document = FileSystemDocumentLoader.loadDocument(path, parser);

            // 使用语义分层切片（替代单一的递归分割）
            var segments = semanticSplitService.split(document);

            if (segments == null || segments.isEmpty()) {
                return "文档内容为空或无法解析";
            }

            // 向量化并存入向量库
            Response<List<dev.langchain4j.data.embedding.Embedding>> response = embeddingModel.embedAll(segments);
            embeddingStore.addAll(response.content());

            // 如果提供了文档ID，则创建版本记录
            if (documentId != null && !documentId.isEmpty()) {
                String fileType = getFileType(filePath);
                DocumentVersion version = documentVersionService.createVersion(
                        documentId,
                        documentName,
                        filePath,
                        fileType,
                        null, // milvusCollectionId - 可根据需要设置
                        segments.size(),
                        versionDescription,
                        uploaderId,
                        uploaderName
                );
                log.info("文档版本记录创建成功: documentId={}, version={}", documentId, version.getVersion());
                return String.format("文档导入成功，共分割：%d 个文本片段，版本号：%d", segments.size(), version.getVersion());
            }

            return "文档导入成功，共分割：" + segments.size() + " 个文本片段";
        } catch (Exception e) {
            log.error("文档加载失败: filePath={}", filePath, e);
            throw new RuntimeException("文档加载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据文件路径获取文件类型
     * @param filePath 文件路径
     * @return 文件类型
     */
    private String getFileType(String filePath) {
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filePath.length() - 1) {
            return filePath.substring(lastDotIndex + 1).toUpperCase();
        }
        return "UNKNOWN";
    }
}
