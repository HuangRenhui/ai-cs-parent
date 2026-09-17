package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文档加载服务（占位）
 *
 * <p>TODO 后续实现：用 PDFBox / Tika 解析文档 → 语义分层切片
 * （{@code SemanticSplitService}）→ 经 {@code EmbeddingModel} 向量化写入
 * {@code EmbeddingStore} 向量集合 → 需要时经 {@code DocumentVersionService}
 * 落一条版本记录并返回片段数与版本号。</p>
 *
 * <p>当前不解析、不切片、不向量化：四个公开入口一律返回未实现文案
 * （「纯文案」占位风格，不伪造导入成功）。</p>
 *
 * <p>本类不接入向量模型与向量库（未实现）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLoadService {

    /** 内部方法桩：未实现时统一的返回文案 */
    private static final String NOT_IMPLEMENTED = "文档导入为占位实现，后端未接入文档解析与向量入库";

    private final RagProperties ragProperties;
    private final DocumentVersionService documentVersionService;
    private final SemanticSplitService semanticSplitService;

    /**
     * 加载PDF文件入库（占位：返回未实现文案）
     * @param filePath PDF文件路径
     * @return 未实现文案
     */
    public String loadPdf(String filePath) {
        log.warn("[占位] PDF 解析入库未实现 filePath={}", filePath);
        return NOT_IMPLEMENTED;
    }

    /**
     * 加载PDF文件入库（带版本管理，占位：返回未实现文案）
     * @param filePath PDF文件路径
     * @param documentId 文档ID
     * @param documentName 文档名称
     * @param versionDescription 版本描述
     * @param uploaderId 上传人ID
     * @param uploaderName 上传人姓名
     * @return 未实现文案
     */
    public String loadPdf(String filePath, String documentId, String documentName, 
                         String versionDescription, Long uploaderId, String uploaderName) {
        log.warn("[占位] PDF 解析入库（带版本）未实现 filePath={} documentId={}", filePath, documentId);
        return NOT_IMPLEMENTED;
    }

    /**
     * 加载txt、docx、md等通用文件（占位：返回未实现文案）
     * @param filePath 文件路径
     * @return 未实现文案
     */
    public String loadCommonFile(String filePath) {
        log.warn("[占位] 通用文件解析入库未实现 filePath={}", filePath);
        return NOT_IMPLEMENTED;
    }

    /**
     * 加载txt、docx、md等通用文件（带版本管理，占位：返回未实现文案）
     * @param filePath 文件路径
     * @param documentId 文档ID
     * @param documentName 文档名称
     * @param versionDescription 版本描述
     * @param uploaderId 上传人ID
     * @param uploaderName 上传人姓名
     * @return 未实现文案
     */
    public String loadCommonFile(String filePath, String documentId, String documentName, 
                                 String versionDescription, Long uploaderId, String uploaderName) {
        log.warn("[占位] 通用文件解析入库（带版本）未实现 filePath={} documentId={}", filePath, documentId);
        return NOT_IMPLEMENTED;
    }
}
