package com.ai.cs.knowledge.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.knowledge.config.ImageProperties;
import com.ai.cs.knowledge.entity.ImageMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 图片向量化服务（占位）
 *
 * <p>TODO 后续实现：把图片元数据（文件名、尺寸、格式、EXIF 等）拼成自然语言描述文本，
 * 附上元数据组装 {@code TextSegment}，经 {@code embeddingModel} 向量化后写入 Chroma
 * （{@code imageEmbeddingStore}），回填 {@code vectorId / vectorized / vectorCollection}；
 * 并提供批量向量化、按自然语言做语义检索（返回命中元数据）与按 vectorId 删除。</p>
 *
 * <p>当前不向量化、不访问向量库：{@link #vectorize} 抛 {@code BusinessException(503)}（与服务不可用语义一致），
 * {@link #batchVectorize} 返回 0，{@link #search} 返回空列表，{@link #deleteByVectorId} 只记日志。</p>
 *
 * <p>本类不接入向量模型与向量库（未实现），仅依赖 {@code ImageProperties}。</p>
 */
@Slf4j
@Service
public class ImageVectorService {

    private final ImageProperties imageProperties;

    public ImageVectorService(ImageProperties imageProperties) {
        this.imageProperties = imageProperties;
    }

    /**
     * 图片元数据向量化入库（占位：抛 503）
     *
     * @param metadata 图片元数据
     * @return 不返回（抛异常）
     */
    public String vectorize(ImageMetadata metadata) {
        log.warn("[占位] 图片向量化未实现 fileId={}", metadata == null ? null : metadata.getFileId());
        throw new BusinessException(503, "图片向量化为占位实现，后端未接入向量模型与 Chroma");
    }

    /**
     * 批量向量化（占位：返回 0）
     *
     * @return 0
     */
    public int batchVectorize(List<ImageMetadata> metadataList) {
        log.warn("[占位] 图片批量向量化未实现，返回 0");
        return 0;
    }

    /**
     * 图片语义检索（占位：返回空列表）
     *
     * @param query      自然语言查询
     * @param maxResults 最大返回条数
     * @return 空列表
     */
    public List<Map<String, Object>> search(String query, int maxResults) {
        log.warn("[占位] 图片语义检索未实现 maxResults={}", maxResults);
        return List.of();
    }

    /**
     * 按 vectorId 删除向量（占位：不删除）
     */
    public void deleteByVectorId(String vectorId) {
        log.info("[占位] 按 vectorId 删除图片向量未实现 vectorId={}", vectorId);
    }
}
