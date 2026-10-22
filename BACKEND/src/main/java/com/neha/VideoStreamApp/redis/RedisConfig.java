package com.neha.VideoStreamApp.redis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;

@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        if (redisPassword != null && !redisPassword.isBlank()) {
            config.setPassword(redisPassword);
        }
        return new JedisConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Redis ke inner JSON ke liye LOCAL ObjectMapper banao (bean NAHI),
        // taaki Spring MVC ka web ObjectMapper clean rahe.
        // Agar ye ObjectMapper @Bean hota to Spring Boot usse HTTP responses
        // ke liye bhi use karta — type-info wrapper JSON se frontend crash ho jata.
        ObjectMapper redisMapper = new ObjectMapper();
        redisMapper.registerModule(new JavaTimeModule());
        redisMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        redisMapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        redisMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        // SECURITY: Yahan KOI polymorphic default typing NAHI use ho rahi.
        //
        // Pehle `activateDefaultTyping(LaissezFaireSubTypeValidator, NON_FINAL)`
        // tha — jo classic deserialization-gadget RCE hai (attacker Redis me
        // likh sake to arbitrary class instantiate ho jati thi).
        // BasicPolymorphicTypeValidator se replace karne par bhi JDK collection
        // types (ArrayList/HashSet) reject ho jate the aur caching silently
        // fail ho jati thi.
        //
        // Ab plain JSON store hota hai (koi type-id nahi) — deserialization
        // ke liye polymorphic resolution ki zaroorat hi nahi. Jo bhi value
        // padhna chahta hai wo explicitly apne target type me convert karta
        // hai (ObjectMapper.convertValue) — koi arbitrary class load nahi hoti.
        redisMapper.deactivateDefaultTyping();

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(redisMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}