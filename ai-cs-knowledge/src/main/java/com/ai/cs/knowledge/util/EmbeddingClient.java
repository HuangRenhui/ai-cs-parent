package com.ai.cs.knowledge.util;

import com.ai.cs.common.llm.DashscopeModelClient;
import com.ai.cs.common.llm.ModelCallException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.List;

/**
 * FAQ / 租户 RAG 向量化入口，委托 {@link DashscopeModelClient}，不再依赖 localhost:8000。
 */
@Slf4j
@Component
public class EmbeddingClient {

    @Resource
    private DashscopeModelClient modelClient;

    public List<Float> getVector(String text) throws IOException {
        try {
            return modelClient.embed(text);
        } catch (ModelCallException e) {
            log.error("获取向量异常, text={}", text, e);
            throw new IOException("获取向量异常: " + e.getMessage(), e);
        }
    }
}
