package com.neha.VideoStreamApp.cache;

import com.neha.VideoStreamApp.redis.UserKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class JwtBlacklistService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis check fail hone par kya karein?
     *
     * true  = fail-CLOSED → token reject (prod/qa: revoked token Redis outage
     *         me bhi accept na ho)
     * false = fail-OPEN  → token allow (dev: local Redis band hone par app
     *         poori tarah down na ho)
     *
     * NOTE: Risk fail-open ka sirf itna hai ki ek revoked ACCESS token apni
     * remaining TTL (max 5 min) tak valid reh sakta hai. Refresh token ke liye
     * ye risk nahi hai — wo DB se validate hota hai (RefreshTokenRepository),
     * Redis se independent.
     */
    private final boolean failClosed;

    public JwtBlacklistService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${app.security.blacklist.fail-closed:false}") boolean failClosed) {
        this.redisTemplate = redisTemplate;
        this.failClosed = failClosed;
    }

    // Token ko remaining validity time ke liye blacklist karein
    public void blacklistToken(String token, long expirationMillis) {
        if (expirationMillis <= 0) {
            log.warn("[JWT-BLACKLIST] Token already expired, skipping blacklist. expiryMillis={}", expirationMillis);
            return;
        }
        try {
            String key = UserKeyGenerator.getJwtBlacklistKey(token);
            redisTemplate.opsForValue().set(key, "revoked", expirationMillis, TimeUnit.MILLISECONDS);
            // Log me raw token nahi — sirf fingerprint (SHA-256 prefix)
            log.info("[JWT-BLACKLIST] Token blacklisted successfully. tokenFp={} ttlMillis={}",
                    UserKeyGenerator.tokenFingerprint(token), expirationMillis);
        } catch (Exception e) {
            log.error("[JWT-BLACKLIST][ERR-JWT-BL-001] Failed to blacklist token. Reason: {}", e.getMessage(), e);
        }
    }

    /**
     * Check karein ki token blacklisted hai ya nahi.
     *
     * SECURITY: Pehle ye hamesha FALSE return karta tha (fail-open) — jisse
     * logout/revoke Redis outage ke dauraan silently no-op ho jata tha.
     * Ab behaviour configurable hai: prod/qa fail-CLOSED, dev fail-OPEN
     * (warna local Redis band karte hi poora login down ho jata).
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String key = UserKeyGenerator.getJwtBlacklistKey(token);
            boolean blacklisted = Boolean.TRUE.equals(redisTemplate.hasKey(key));
            if (blacklisted) {
                log.info("[JWT-BLACKLIST] Token IS blacklisted (rejected request). tokenFp={}",
                        UserKeyGenerator.tokenFingerprint(token));
            }
            return blacklisted;
        } catch (Exception e) {
            if (failClosed) {
                log.error("[JWT-BLACKLIST][ERR-JWT-BL-002] Redis check failed — failing CLOSED, "
                        + "token will be rejected. Reason: {}", e.getMessage(), e);
                return true;
            }
            log.error("[JWT-BLACKLIST][ERR-JWT-BL-002] Redis check failed — failing OPEN "
                    + "(dev mode). Reason: {}", e.getMessage(), e);
            return false;
        }
    }
}
