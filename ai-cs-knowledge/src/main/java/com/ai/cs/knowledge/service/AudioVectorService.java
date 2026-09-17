package com.ai.cs.knowledge.service;

import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.knowledge.config.AudioProperties;
import com.ai.cs.knowledge.entity.AudioMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 音频向量化服务（占位）
 *
 * <p>TODO 后续实现：把音频元数据（文件名、格式、时长、标签、**转录文本**等）拼成自然语言描述，
 * 附元数据组装 {@code TextSegment}，经 {@code embeddingModel} 向量化后写入 Chroma
 * （{@code audioEmbeddingStore}），回填 {@code vectorId / vectorized / vectorCollection}；
 * 并提供批量向量化、语义检索（返回命中元数据）、按 vectorId 删除，
 * 以及「补上转录文本后重新向量化」的 {@link #reVectorizeWithTranscription}。</p>
 *
 * <p>当前不向量化、不访问向量库：需要入库的两个方法抛 {@code BusinessException(503)}
 * （与服务不可用语义一致），{@link #batchVectorize} 返回 0，{@link #search} 返回空列表，
 * {@link #deleteByVectorId} 只记日志。</p>
 *
 * <p>本类不接入向量模型与向量库（未实现），仅依赖 {@code AudioProperties}。</p>
 */
@Slf4j
@Service
public class AudioVectorService {

    private final AudioProperties audioProperties;

    public AudioVectorService(AudioProperties audioProperties) {
        this.audioProperties = audioProperties;
    }

    /**
     * 音频元数据向量化入库（占位：抛 503）
     *
     * @param metadata 音频元数据
     * @return 不返回（抛异常）
     */
    public String vectorize(AudioMetadata metadata) {
        log.warn("[占位] 音频向量化未实现 fileId={}", metadata == null ? null : metadata.getFileId());
        throw new BusinessException(503, "音频向量化为占位实现，后端未接入向量模型与 Chroma");
    }

    /**
     * 批量向量化（占位：返回 0）
     *
     * @return 0
     */
    public int batchVectorize(List<AudioMetadata> metadataList) {
        log.warn("[占位] 音频批量向量化未实现，返回 0");
        return 0;
    }

    /**
     * 音频语义检索（占位：返回空列表）
     *
     * @param query      自然语言查询
     * @param maxResults 最大返回条数
     * @return 空列表
     */
    public List<Map<String, Object>> search(String query, int maxResults) {
        log.warn("[占位] 音频语义检索未实现 maxResults={}", maxResults);
        return List.of();
    }

    /**
     * 按 vectorId 删除向量（占位：不删除）
     */
    public void deleteByVectorId(String vectorId) {
        log.info("[占位] 按 vectorId 删除音频向量未实现 vectorId={}", vectorId);
    }

    /**
     * 带上转录文本重新向量化（占位：抛 503）
     *
     * @param metadata 音频元数据（含转录文本）
     * @return 不返回（抛异常）
     */
    public String reVectorizeWithTranscription(AudioMetadata metadata) {
        log.warn("[占位] 音频带转录重新向量化未实现 fileId={}", metadata == null ? null : metadata.getFileId());
        throw new BusinessException(503, "音频重新向量化为占位实现，后端未接入向量模型与 Chroma");
    }
}
