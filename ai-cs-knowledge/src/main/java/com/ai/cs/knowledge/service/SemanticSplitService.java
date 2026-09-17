package com.ai.cs.knowledge.service;

import com.ai.cs.knowledge.config.RagProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 语义切片服务（占位）
 *
 * <p>TODO 后续实现：按 {@code RagProperties.Split} 配置切片——
 * 支持多粒度切分（每个 chunk 同时保留粗粒度与细粒度信息）、
 * 以段落/句子/标点等语义分隔符切分（带重叠、按正则构建分隔符）、
 * 中英文混合的句子切分、以及切片文本归一化（压缩空白 + 转小写，用于去重比较）。</p>
 *
 * <p>当前不切片：{@link #split} 返回空列表，上层切片流程会得到空结果。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSplitService {

    private final RagProperties ragProperties;

    /**
     * 语义切片（占位：返回空列表）
     *
     * @param document 待切片文档
     * @return 空列表
     */
    public List<TextSegment> split(Document document) {
        log.warn("[占位] 语义切片未实现，返回空列表");
        return List.of();
    }
}
