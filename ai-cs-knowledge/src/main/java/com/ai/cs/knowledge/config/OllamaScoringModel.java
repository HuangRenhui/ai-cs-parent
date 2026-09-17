package com.ai.cs.knowledge.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Ollama Rerank 评分模型（占位）
 *
 * <p>TODO 后续实现：调用 Ollama 的 {@code /v1/rerank} 接口对候选片段打分并返回相关性分数（0~1）。
 * 需处理两类响应格式（带 results/scores 字段的完整格式与 {@code [0.95, 0.87, ...]} 简化格式）、
 * 分数截断到 [0,1] 区间、JSON 字符串转义，并在失败时返回中性分或抛出让上层降级。</p>
 *
 * <p>当前不发起 HTTP 调用：{@link #score} 与 {@link #scoreAll} 一律返回 0 分（中性，不伪造相关性）。</p>
 */
@Slf4j
public class OllamaScoringModel implements ScoringModel {

    private final String baseUrl;
    private final String modelName;

    /**
     * @param baseUrl   Ollama 服务地址
     * @param modelName Rerank 模型名（如 BGE-Reranker）
     */
    public OllamaScoringModel(String baseUrl, String modelName) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        log.info("[占位] Ollama Rerank 评分模型构造 baseUrl={} model={}（不建立 HTTP 连接）", baseUrl, modelName);
    }

    /**
     * 单片段相关性评分（占位：恒返回 0）
     *
     * @return 0 分
     */
    @Override
    public Response<Double> score(TextSegment segment, String query) {
        log.info("[占位] Rerank 单条评分未实现，返回 0 baseUrl={} model={}", baseUrl, modelName);
        return Response.from(0.0);
    }

    /**
     * 批量相关性评分（占位：全部返回 0）
     *
     * @return 与入参等长的 0 分列表
     */
    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        log.info("[占位] Rerank 批量评分未实现，返回全 0（片段数={}）", segments == null ? 0 : segments.size());
        List<Double> zeros = segments == null ? List.of() : segments.stream().map(s -> 0.0).toList();
        return Response.from(zeros);
    }
}
