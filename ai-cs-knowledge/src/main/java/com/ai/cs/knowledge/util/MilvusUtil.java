package com.ai.cs.knowledge.util;

/**
 *
 * @author huangrenhui
 * @date 2026/6/11 17:58
 * @description Milvus 连接工具类 & 知识库检索
 * Milvus 提前执行集合创建，用于存储问题向量，此处提供基础增删查代码
 */

import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class MilvusUtil {
    @Value("${milvus.host}")
    private String host;
    @Value("${milvus.port}")
    private Integer port;

    private MilvusClient client;
    public static final String COLLECTION_NAME = "cs_faq_collection";

    @PostConstruct
    public void init() {
        ConnectParam param = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build();
        client = new MilvusServiceClient(param);
    }

    public MilvusClient getClient() {
        return client;
    }
}