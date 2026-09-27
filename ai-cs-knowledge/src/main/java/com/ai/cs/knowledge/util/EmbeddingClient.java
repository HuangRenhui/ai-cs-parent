package com.ai.cs.knowledge.util;

import com.ai.cs.common.llm.ModelCallException;
import com.ai.cs.common.llm.ModelRouter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

/**
 * FAQ / 租户 RAG 向量化入口（占位）
 *
 * <p>TODO 后续实现：底层统一走 {@link ModelRouter}（未登记向量模型时回退历史配置，登记后支持向量模型切换与故障转移）；
 * 需保留 {@link IOException} 异常语义（包装 {@code ModelCallException}），屏蔽上层对具体异常的依赖。</p>
 *
 * <p>当前不调用模型：{@link #getVector} 一律抛 {@link IOException}（占位），向量化相关的上层流程按异常分支降级。</p>
 */
@Slf4j
@Component
public class EmbeddingClient {

    @Resource
    private ModelRouter modelRouter;

    /**
     * 获取文本的 Embedding 向量（占位：直接抛 IOException）
     *
     * @param text 待向量化文本
     * @return 不返回（抛异常）
     * @throws IOException 占位实现
     */
    public List<Float> getVector(String text) throws IOException {
        if (!StringUtils.hasText(text)) {
            throw new IOException("获取向量异常: 待向量化文本为空");
        }
        try {
            List<Float> vector = modelRouter.embed(text);
            if (vector == null || vector.isEmpty()) {
                throw new IOException("获取向量异常: 向量为空");
            }
            return vector;
        } catch (ModelCallException e) {
            throw new IOException("获取向量异常: " + e.getMessage(), e);
        }
    }
}
