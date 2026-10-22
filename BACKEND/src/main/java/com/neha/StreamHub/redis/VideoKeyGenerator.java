package com.neha.StreamHub.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

@Slf4j
@Component
public class VideoKeyGenerator {

    private static final String VIDEO_KEY_PREFIX = "video:scroll";

    public String generateScrollCacheKey(
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
        StringJoiner joiner = new StringJoiner(":");
        joiner.add(VIDEO_KEY_PREFIX);

        addIfNotNull(joiner, "search", search);
        addIfNotNull(joiner, "userId", userId);
        addIfNotNull(joiner, "createdAfter", createdAfter != null ? createdAfter.toString() : null);
        addIfNotNull(joiner, "createdBefore", createdBefore != null ? createdBefore.toString() : null);
        addIfNotNull(joiner, "contentType", contentType);
        addIfNotNull(joiner, "category", category);
        addIfNotNull(joiner, "scrollId", scrollId);

        joiner.add("pageSize=" + pageSize);
        joiner.add("sortBy=" + (sortBy != null ? sortBy : "createdAt"));
        joiner.add("sortDirection=" + (sortDirection != null ? sortDirection.name() : "DESC"));

        String generatedKey = joiner.toString();
        log.debug("Generated Redis Scroll Key: {}", generatedKey);

        return generatedKey;
    }

    public String generateScrollCachePattern() {
        return VIDEO_KEY_PREFIX + ":*";
    }

    private void addIfNotNull(StringJoiner joiner, String key, Object value) {
        if (value != null && !value.toString().trim().isEmpty()) {
            joiner.add(key + "=" + value);
        }
    }
}