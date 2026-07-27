package com.ai.cs.knowledge.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于Ollama Rerank API的评分模型实现
 * 使用本地部署的BGE-Reranker模型进行文档重排序
 * 
 * @author huangrenhui
 * @date 2026/6/24
 */
@Slf4j
public class OllamaScoringModel implements ScoringModel {

    private final String baseUrl;
    private final String modelName;
    private final OkHttpClient httpClient;

    public OllamaScoringModel(String baseUrl, String modelName) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.httpClient = new OkHttpClient();
        log.info("OllamaScoringModel初始化完成，URL: {}, Model: {}", baseUrl, modelName);
    }

    /**
     * 对单个文本段打分
     * @param segment 文本片段
     * @param query 查询问题
     * @return 相关性分数（0-1之间）
     */
    @Override
    public Response<Double> score(TextSegment segment, String query) {
        try {
            List<Double> scores = scoreAll(List.of(segment), query).content();
            return Response.from(scores.get(0));
        } catch (Exception e) {
            log.error("Ollama Rerank评分失败", e);
            return Response.from(0.0);
        }
    }

    /**
     * 批量打分（推荐使用，一次推理多条）
     * @param segments 文本片段列表
     * @param query 查询问题
     * @return 相关性分数列表
     */
    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        try {
            // 提取文本内容
            List<String> documents = new ArrayList<>();
            for (TextSegment segment : segments) {
                documents.add(segment.text());
            }

            // 调用Ollama Rerank API
            List<Double> scores = callOllamaRerank(query, documents);
            return Response.from(scores);
        } catch (Exception e) {
            log.error("Ollama Rerank批量评分失败", e);
            // 返回默认分数（避免流程中断）
            List<Double> defaultScores = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                defaultScores.add(0.5);
            }
            return Response.from(defaultScores);
        }
    }

    /**
     * 调用Ollama Rerank API
     * 注意：需要Ollama支持/v1/rerank接口（较新版本）
     */
    private List<Double> callOllamaRerank(String query, List<String> documents) throws IOException {
        // 构建请求体
        StringBuilder jsonBody = new StringBuilder();
        jsonBody.append("{");
        jsonBody.append("\"model\":\"").append(modelName).append("\",");
        jsonBody.append("\"query\":\"").append(escapeJson(query)).append("\",");
        jsonBody.append("\"documents\":[");
        
        for (int i = 0; i < documents.size(); i++) {
            if (i > 0) jsonBody.append(",");
            jsonBody.append("\"").append(escapeJson(documents.get(i))).append("\"");
        }
        jsonBody.append("]");
        jsonBody.append("}");

        // 发送HTTP请求
        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.get("application/json;charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(baseUrl + "/api/rerank")
                .post(body)
                .build();

        try (okhttp3.Response httpResponse = httpClient.newCall(request).execute()) {
            if (!httpResponse.isSuccessful() || httpResponse.body() == null) {
                log.warn("Ollama Rerank API调用失败，状态码: {}，降级为向量相似度排序", httpResponse.code());
                // 降级策略：返回均匀分数，由后续逻辑处理
                List<Double> fallbackScores = new ArrayList<>();
                for (int i = 0; i < documents.size(); i++) {
                    fallbackScores.add(0.5);
                }
                return fallbackScores;
            }

            // 解析响应（简化实现，实际需要根据Ollama API返回格式调整）
            String responseBody = httpResponse.body().string();
            log.debug("Ollama Rerank响应: {}", responseBody);
            
            // TODO: 根据实际的Ollama Rerank API响应格式解析分数
            // 这里提供一个简化的实现，假设返回JSON数组格式的分数
            List<Double> scores = parseScoresFromResponse(responseBody, documents.size());
            return scores;
        }
    }

    /**
     * 从响应中解析分数
     * 支持Ollama Rerank API标准返回格式：
     * {"results": [{"index": 0, "relevance_score": 0.95}, ...]}
     * 以及简化格式：[0.95, 0.87, 0.72, ...]
     */
    private List<Double> parseScoresFromResponse(String responseBody, int expectedSize) {
        List<Double> scores = new ArrayList<>();

        try {
            // 尝试解析标准Ollama Rerank API返回格式
            // 格式: {"results": [{"index": 0, "relevance_score": 0.95}, ...]}
            if (responseBody.contains("\"relevance_score\"")) {
                String[] parts = responseBody.split("\"relevance_score\":");
                for (int i = 1; i < parts.length && scores.size() < expectedSize; i++) {
                    String afterScore = parts[i].trim();
                    // 提取逗号或}之前的数值
                    int endIdx = 0;
                    while (endIdx < afterScore.length() && 
                           (Character.isDigit(afterScore.charAt(endIdx)) || afterScore.charAt(endIdx) == '.' || afterScore.charAt(endIdx) == '-')) {
                        endIdx++;
                    }
                    if (endIdx > 0) {
                        double score = Double.parseDouble(afterScore.substring(0, endIdx));
                        scores.add(clampScore(score));
                    }
                }
            } 
            // 尝试解析简化数组格式: [0.95, 0.87, ...]
            else if (responseBody.trim().startsWith("[")) {
                String numbers = responseBody.replaceAll("[\\[\\]\\s]", "");
                String[] numParts = numbers.split(",");
                for (String num : numParts) {
                    if (!num.isEmpty() && scores.size() < expectedSize) {
                        double score = Double.parseDouble(num.trim());
                        scores.add(clampScore(score));
                    }
                }
            }
            // 尝试解析单行分数格式（逗号分隔）
            else {
                String[] numParts = responseBody.trim().split(",");
                for (String num : numParts) {
                    if (!num.isEmpty() && scores.size() < expectedSize) {
                        double score = Double.parseDouble(num.trim());
                        scores.add(clampScore(score));
                    }
                }
            }

            // 如果解析出的分数不足，用默认值补齐
            while (scores.size() < expectedSize) {
                scores.add(0.5);
                log.warn("Rerank分数不足，用默认值0.5补齐，已解析: {}，期望: {}", scores.size() - 1, expectedSize);
            }
        } catch (Exception e) {
            log.error("解析Rerank响应失败，降级为默认分数", e);
            scores.clear();
            for (int i = 0; i < expectedSize; i++) {
                scores.add(0.5);
            }
        }

        return scores;
    }

    /**
     * 将分数限制在[0, 1]有效范围内
     */
    private double clampScore(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
