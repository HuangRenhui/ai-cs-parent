package com.ai.cs.knowledge.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis持久化对话记忆存储
 * 实现LangChain4j的ChatMemoryStore接口，将对话历史存储到Redis中
 */
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RagProperties ragProperties;
    private ValueOperations<String, Object> ops;

    private static final String KEY_PREFIX = "rag:chat:memory:";

    public RedisChatMemoryStore(RedisTemplate<String, Object> redisTemplate, RagProperties ragProperties) {
        this.redisTemplate = redisTemplate;
        this.ragProperties = ragProperties;
    }

    /**
     * 初始化序列化器与Value操作句柄
     */
    @PostConstruct
    public void init() {
        // 设置序列化，避免乱码
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        ops = redisTemplate.opsForValue();
    }

    /**
     * 拼接Redis存储key（统一前缀，便于批量管理与隔离）
     */
    private String getKey(String memoryId) {
        return KEY_PREFIX + memoryId;
    }

    /**
     * 读取指定会话的对话历史
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = getKey((String) memoryId);
        return (List<ChatMessage>) ops.get(key);
    }

    /**
     * 更新指定会话的对话历史，并按配置刷新过期时间
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = getKey((String) memoryId);
        long ttl = ragProperties.getChatMemory().getTtl();
        ops.set(key, messages, Duration.ofSeconds(ttl));
    }

    /**
     * 删除指定会话的对话历史；传入"*"时清空全部会话记忆
     */
    @Override
    public void deleteMessages(Object memoryId) {
        String key = getKey((String) memoryId);
        // 如果memoryId是通配符"*"，则删除所有记忆
        if ("*".equals(memoryId)) {
            redisTemplate.delete(redisTemplate.keys(KEY_PREFIX + "*"));
        } else {
            redisTemplate.delete(key);
        }
    }
}
