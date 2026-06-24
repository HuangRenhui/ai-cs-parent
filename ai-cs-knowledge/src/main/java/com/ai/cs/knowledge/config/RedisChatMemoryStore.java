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

    @PostConstruct
    public void init() {
        // 设置序列化，避免乱码
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        ops = redisTemplate.opsForValue();
    }

    private String getKey(String memoryId) {
        return KEY_PREFIX + memoryId;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = getKey((String) memoryId);
        return (List<ChatMessage>) ops.get(key);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = getKey((String) memoryId);
        long ttl = ragProperties.getChatMemory().getTtl();
        ops.set(key, messages, Duration.ofSeconds(ttl));
    }

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
