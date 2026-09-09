package com.ai.cs.knowledge.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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

            String responseBody = httpResponse.body().string();
            log.debug("Ollama Rerank响应: {}", responseBody);
            
            List<Double> scores = parseScoresFromResponse(responseBody, documents.size());
            return scores;
        }
    }

    /**
     * 从响应中解析分数（FastJSON实现）
     * 支持Ollama Rerank API标准返回格式：
     * {"results": [{"index": 0, "relevance_score": 0.95}, ...]}
     * 以及简化格式：[0.95, 0.87, 0.72, ...]
     */
    private List<Double> parseScoresFromResponse(String responseBody, int expectedSize) {
        List<Double> scores = new ArrayList<>();

        try {
            String trimmed = responseBody.trim();
            if (trimmed.startsWith("{")) {
                JSONObject json = JSON.parseObject(responseBody);
                JSONArray results = json.getJSONArray("results");
                if (results != null) {
                    for (int i = 0; i < results.size() && scores.size() < expectedSize; i++) {
                        JSONObject item = results.getJSONObject(i);
                        Double relevanceScore = item.getDouble("relevance_score");
                        if (relevanceScore == null) {
                            relevanceScore = item.getDouble("score");
                        }
                        if (relevanceScore != null) {
                            scores.add(clampScore(relevanceScore));
                        }
                    }
                }
            } else if (trimmed.startsWith("[")) {
                JSONArray arr = JSON.parseArray(responseBody);
                for (int i = 0; i < arr.size() && scores.size() < expectedSize; i++) {
                    Double val = arr.getDouble(i);
                    if (val != null) {
                        scores.add(clampScore(val));
                    }
                }
            }

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