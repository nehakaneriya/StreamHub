package com.neha.VideoStreamApp.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed rate limiter / account lockout.
 *
 * SECURITY: Login, admin-login aur resend-OTP endpoints pe pehle koi bhi
 * protection nahi thi — password guessing, 6-digit OTP guessing (10^6 combos)
 * aur OTP/email spam sab unlimited the.
 *
 * Usage pattern:
 *   if (rateLimitService.isBlocked(key)) throw ...;
 *   try { ...success... rateLimitService.reset(key); }
 *   catch (...) { rateLimitService.recordFailure(key); throw ...; }
 */
@Service
@Slf4j
public class RateLimitService {

    private static final String PREFIX = "ratelimit:";

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.ratelimit.login.max-attempts:5}")
    private int loginMaxAttempts;

    @Value("${app.ratelimit.login.window-minutes:15}")
    private long loginWindowMinutes;

    @Value("${app.ratelimit.otp.max-attempts:5}")
    private int otpMaxAttempts;

    @Value("${app.ratelimit.otp.window-minutes:15}")
    private long otpWindowMinutes;

    @Value("${app.ratelimit.resend.max-attempts:3}")
    private int resendMaxAttempts;

    @Value("${app.ratelimit.resend.window-minutes:60}")
    private long resendWindowMinutes;

    public RateLimitService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ── Login (per email) ───────────────────────────────────────────────
    public boolean isLoginBlocked(String identifier) {
        return isBlocked(key("login", identifier), loginMaxAttempts);
    }

    public void recordLoginFailure(String identifier) {
        recordFailure(key("login", identifier), Duration.ofMinutes(loginWindowMinutes));
    }

    public void resetLoginFailures(String identifier) {
        reset(key("login", identifier));
    }

    // ── OTP verify (per email) ──────────────────────────────────────────
    public boolean isOtpBlocked(String identifier) {
        return isBlocked(key("otp", identifier), otpMaxAttempts);
    }

    public void recordOtpFailure(String identifier) {
        recordFailure(key("otp", identifier), Duration.ofMinutes(otpWindowMinutes));
    }

    public void resetOtpFailures(String identifier) {
        reset(key("otp", identifier));
    }

    // ── OTP resend (per email + per IP) ────────────────────────────────
    public boolean isResendBlocked(String identifier) {
        return isBlocked(key("resend", identifier), resendMaxAttempts);
    }

    public void recordResend(String identifier) {
        recordFailure(key("resend", identifier), Duration.ofMinutes(resendWindowMinutes));
    }

    // ── Generic helpers ────────────────────────────────────────────────

    /**
     * Kya is identifier ke attempts limit cross kar chuke hain?
     *
     * NOTE: Redis down hone par ye FAIL-OPEN hai (block nahi karta). Pehle
     * fail-closed tha — jisse Redis band hone par login hi completely
     * block ho jati thi ("Too many failed login attempts"), jabki actually
     * koi attempt hi nahi hua tha.
     *
     * Ye sahi trade-off hai: rate limiter ek defence-in-depth layer hai,
     * primary control password hashing + account lockout hai. Ek transient
     * Redis outage par poora auth flow down karna worse hai.
     */
    public boolean isBlocked(String key, int maxAttempts) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) {
                return false;
            }
            int attempts = Integer.parseInt(value);
            return attempts >= maxAttempts;
        } catch (NumberFormatException e) {
            return false;
        } catch (Exception e) {
            // Fail-OPEN — Redis down ho to rate limit skip karo, login mat roko
            log.error("[RATE-LIMIT] Redis check failed — skipping rate limit (fail-open). key={} Reason: {}", key, e.getMessage());
            return false;
        }
    }

    public void recordFailure(String key, Duration window) {
        try {
            String current = stringRedisTemplate.opsForValue().get(key);
            int attempts = 0;
            if (current != null) {
                try {
                    attempts = Integer.parseInt(current);
                } catch (NumberFormatException ignored) {
                    attempts = 0;
                }
            }
            attempts++;
            stringRedisTemplate.opsForValue().set(key, String.valueOf(attempts), window);
        } catch (Exception e) {
            log.error("[RATE-LIMIT] Could not record failure. key={} Reason: {}", key, e.getMessage());
        }
    }

    public void reset(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.error("[RATE-LIMIT] Could not reset counter. key={} Reason: {}", key, e.getMessage());
        }
    }

    private String key(String scope, String identifier) {
        String normalized = identifier == null ? "unknown" : identifier.trim().toLowerCase();
        return PREFIX + scope + ":" + normalized;
    }
}
