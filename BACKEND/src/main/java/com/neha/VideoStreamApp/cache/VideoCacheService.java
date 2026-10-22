package com.neha.VideoStreamApp.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neha.VideoStreamApp.dtos.response.ScrollResponse;
import com.neha.VideoStreamApp.dtos.response.VideoDto;
import com.neha.VideoStreamApp.redis.VideoKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
@RequiredArgsConstructor
public class VideoCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final VideoKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    // Cache TTL in minutes configuration
    private static final long CACHE_TTL_MINUTES = 10;
    private static final int SCAN_BATCH_SIZE = 500;
    private static final int MAX_KEYS_PER_EVICTION = 10_000;
    private Logger logger = org.slf4j.LoggerFactory.getLogger(VideoCacheService.class);


    //get cached scroll response
    public ScrollResponse<VideoDto> getCachedScrollResponse(
            String search,
            UUID userId,
            Instant createdAfter,
            Instant createdBefore,
            String contentType,
            String category,
            String scrollId,
            int pageSize,
            String sortBy,
            Sort.Direction sortDirection
    ) {
        String cacheKey = keyGenerator.generateScrollCacheKey(
                search,
                userId,
                createdAfter,
                createdBefore,
                contentType,
                category,
                scrollId,
                pageSize,
                sortBy,
                sortDirection);
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                logger.info("[VIDEO-CACHE] CACHE HIT. key={}", cacheKey);
                return objectMapper.convertValue(cached, new TypeReference<ScrollResponse<VideoDto>>() {});
            } else {
                logger.info("[VIDEO-CACHE] CACHE MISS. key={}", cacheKey);
                return null;
            }
        }
        catch (Exception e) {
            logger.error("[VIDEO-CACHE][ERR-VC-001] Error retrieving cached scroll response. key={} Error: {}", cacheKey, e.getMessage(), e);
            return null;
        }
    }

    //cache scroll response
    public void cacheScrollResponse(
            ScrollResponse<VideoDto> scrollResponse,
            String search,
            UUID userId,
            Instant createdAfter,
            Instant createdBefore,
            String contentType,
            String category,
            String scrollId,
            int pageSize,
            String sortBy,
            Sort.Direction sortDirection
    ) {
        String cacheKey = keyGenerator.generateScrollCacheKey(
                search,
                userId,
                createdAfter,
                createdBefore,
                contentType,
                category,
                scrollId,
                pageSize,
                sortBy,
                sortDirection);

        try{
            redisTemplate.opsForValue().set(
                    cacheKey,
                    scrollResponse,
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES);

            logger.info("[VIDEO-CACHE] Scroll response cached. key={} ttlMinutes={}", cacheKey, CACHE_TTL_MINUTES);
        } catch (Exception e) {
            logger.error("[VIDEO-CACHE][ERR-VC-002] Error caching scroll response. key={} Error: {}", cacheKey, e.getMessage(), e);
        }
    }

    /**
     * Scroll cache evict karo.
     *
     * SECURITY/PERF: Pehle `redisTemplate.keys(pattern)` use hota tha — jo Redis ka
     * POORA keyspace block karke scan karta hai. Ye har view increment / upload /
     * update / delete par call hota tha, aur view endpoint public hai — matlab
     * koi bhi baar baar request bhej ke Redis event loop block kar sakta tha.
     *
     * Ab incremental `SCAN` cursor use hota hai jo Redis ko block nahi karta.
     */
    /**
     * Cache eviction transaction COMMIT ke baad kare.
     *
     * Agar eviction @Transactional method ke andar hoti hai to ek concurrent read
     * eviction aur commit ke beech cache ko PRE-COMMIT data se repopulate kar
     * deti thi — jisse stale feed 10 minute tak dikhti rahi.
     */
    public void evictScrollCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        evictScrollCache();
                    } catch (Exception e) {
                        logger.error("[VIDEO-CACHE][ERR-VC-004] Cache eviction after commit failed. Error: {}", e.getMessage());
                    }
                }
            });
        } else {
            evictScrollCache();
        }
    }

    public void evictScrollCache() {
        String pattern = keyGenerator.generateScrollCachePattern();
        try {
            // Incremental SCAN — Redis ko block nahi karta (KEYS command poora
            // keyspace ek hi call me scan karta hai).
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                    // Bounded batch — memory blow-up na ho
                    if (keys.size() >= MAX_KEYS_PER_EVICTION) {
                        break;
                    }
                }
            }
            if (!keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                logger.info("[VIDEO-CACHE] Scroll cache evicted. pattern={} keysDeleted={}", pattern, deleted);
            } else {
                logger.debug("[VIDEO-CACHE] No scroll cache keys found for eviction. pattern={}", pattern);
            }
        } catch (Exception e) {
            logger.error("[VIDEO-CACHE][ERR-VC-003] Error evicting scroll cache. pattern={} Error: {}", pattern, e.getMessage(), e);
        }
    }
}
