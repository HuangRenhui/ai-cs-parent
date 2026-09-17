package com.ai.cs.knowledge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类
 */
@Configuration
public class RedisConfig {

    /**
     * 构建RedisTemplate Bean
     * key使用String序列化，value使用JSON序列化，便于在Redis中直接阅读存储内容
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key/hashKey用String序列化，避免默认JDK序列化产生不可读的二进制前缀
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        // value/hashValue用JSON序列化（附带类型信息），支持任意对象的存取与反序列化
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);
        // 序列化器设置完成后初始化模板
        template.afterPropertiesSet();
        return template;
    }
}
