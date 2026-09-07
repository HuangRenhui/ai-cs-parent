package com.ai.cs.knowledge.util;

import com.ai.cs.common.llm.ModelCallException;
import com.ai.cs.common.llm.ModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.List;

/**
 * FAQ / 租户 RAG 向量化入口，底层统一走 {@link ModelRouter}：
 * 未登记向量模型时回退历史配置，登记后支持向量模型切换与故障转移。
 */
@Slf4j
@Component
public class EmbeddingClient {

    @Resource
    private ModelRouter modelRouter;

    public List<Float> getVector(String text) throws IOException {
        try {
            return modelRouter.embed(text);
        } catch (ModelCallException e) {
            log.error("获取向量异常, text={}", text, e);
            throw new IOException("获取向量异常: " + e.getMessage(), e);
        }
    }
}
