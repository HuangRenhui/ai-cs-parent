package com.ai.cs.knowledge.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;

/**
 *
 * @author huangrenhui
 * @date 2026/6/17 21:57
 * @description Embedding向量化客户端
 */

@Slf4j
@Component
public class EmbeddingClient {
    
    @Value("${embedding.url:http://127.0.0.1:8000/embedding}")
    private String embedUrl;
    
    private OkHttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient();
        log.info("EmbeddingClient初始化完成，URL: {}", embedUrl);
    }

    public List<Float> getVector(String text) throws IOException {
        JSONObject req = new JSONObject();
        req.put("text", text);
        RequestBody body = RequestBody.create(JSON.toJSONString(req),
                MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(embedUrl)
                .post(body)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("调用Embedding服务失败，状态码: {}", response.code());
                throw new IOException("调用Embedding服务失败: " + response.code());
            }
            JSONObject json = JSON.parseObject(response.body().string());
            JSONArray vecArr = json.getJSONArray("embedding");
            return vecArr.toJavaList(Float.class);
        } catch (Exception e) {
            log.error("获取向量异常, text={}", text, e);
            throw new IOException("获取向量异常: " + e.getMessage(), e);
        }
    }
}
