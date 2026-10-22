package com.neha.VideoStreamApp.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neha.VideoStreamApp.dtos.common.UserCacheDto;
import com.neha.VideoStreamApp.entities.Role;
import com.neha.VideoStreamApp.entities.User;
import com.neha.VideoStreamApp.redis.UserKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;


import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final long CACHE_TTL_MINUTES = 60;

    public UserCacheService(RedisTemplate<String, Object> redisTemplate,ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public void cacheUser(String userId, User user) {
        String key = UserKeyGenerator.getUserCacheKey(userId);
        try {
            UserCacheDto dto = UserCacheDto.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .name(user.getName())
                    .enable(user.isEnable())
                    .roles(user.getRoles() == null ? Set.of() :
                            user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                    .build();

            redisTemplate.opsForValue().set(key, dto, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.info("[USER-CACHE] User cached successfully. key={} ttlMinutes={}", key, CACHE_TTL_MINUTES);
        } catch (Exception e) {
            log.error("[USER-CACHE][ERR-UC-001] Failed to cache user. key={} Reason: {}", key, e.getMessage(), e);
        }
    }

    public User getCachedUser(String userId) {
        String key = UserKeyGenerator.getUserCacheKey(userId);
        try {
            Object obj = redisTemplate.opsForValue().get(key);

            if (obj == null) {
                log.info("[USER-CACHE] CACHE MISS (DB fallback hoga). key={}", key);
                return null;
            }
            log.info("[USER-CACHE] CACHE HIT. key={} cachedType={}", key, obj.getClass().getSimpleName());

            if (obj instanceof User) {
                return (User) obj;
            }

            // Redis me plain JSON store hota hai (polymorphic typing nahi — security
            // ke liye). Value wapas aati hai Map form me, isliye explicitly apne
            // target type me convert karte hain.
            UserCacheDto dto;
            if (obj instanceof UserCacheDto already) {
                dto = already;
            } else {
                dto = objectMapper.convertValue(obj, UserCacheDto.class);
            }

            if (dto == null) {
                return null;
            }

            // Cache me UserCacheDto store hota hai — roles String me rehta hai.
            // Direct convertValue(User.class) se roles Set<String> → Set<Role> conversion fail hota
            // tha aur har request DB pe chali jati thi. Ab khud se sahi User banao.
            return User.builder()
                    .id(dto.getId())
                    .email(dto.getEmail())
                    .name(dto.getName())
                    .enable(dto.isEnable())
                    .roles(dto.getRoles() == null ? new HashSet<>() :
                            dto.getRoles().stream()
                                    .map(name -> Role.builder().name(name).build())
                                    .collect(Collectors.toSet()))
                    .build();
        } catch (Exception e) {
            // Redis deserialization fail hone par app crash hone ke bajaye DB hit handle ho jayega
            log.error("[USER-CACHE][ERR-UC-002] Redis read/deserialize failed, falling back to DB. key={} Reason: {}", key, e.getMessage(), e);
            return null;
        }
    }

    public void evictUserCache(String userId) {
        String key = UserKeyGenerator.getUserCacheKey(userId);
        try {
            redisTemplate.delete(key);
            log.info("[USER-CACHE] User cache evicted. key={}", key);
        } catch (Exception e) {
            log.error("[USER-CACHE][ERR-UC-003] Failed to evict user cache. key={} Reason: {}", key, e.getMessage(), e);
        }
    }
}

