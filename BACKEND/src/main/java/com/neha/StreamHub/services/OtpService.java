package com.neha.StreamHub.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@Slf4j
public class OtpService {

    private static final String OTP_PREFIX = "otp:code:";
    private static final String OTP_COOLDOWN_PREFIX = "otp:cooldown:";
    private static final String OTP_ATTEMPTS_PREFIX = "otp:attempts:";

    private static final int MAX_OTP_ATTEMPTS = 5;

    private final StringRedisTemplate stringRedisTemplate;
    private final long otpTtlSeconds;
    private final long resendCooldownSeconds;
    private final boolean logOtp;

    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(
            StringRedisTemplate stringRedisTemplate,
            @Value("${app.otp.ttl-seconds:300}") long otpTtlSeconds,
            @Value("${app.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${app.otp.log-otp:false}") boolean logOtp) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.otpTtlSeconds = otpTtlSeconds;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.logOtp = logOtp;
    }

    // Naya 6-digit OTP generate + Redis me store + return karo
    public String generateAndStore(String email) {
        String key = emailKey(email);
        stringRedisTemplate.delete(key);

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(key, otp, Duration.ofSeconds(otpTtlSeconds));

        // Resend cooldown bhi set karo — is gap ke andar resend allow nahi hoga
        stringRedisTemplate.opsForValue().set(cooldownKey(email), "1", Duration.ofSeconds(resendCooldownSeconds));

        // DEV ONLY: OTP log karo taaki SMTP fail hone par bhi flow test kiya ja sake.
        // Prod/QA me default false hai (OTP log karna security risk hai).
        if (logOtp) {
            log.info("[OTP][DEV] OTP for {} = {} (ttl={}s)", email, otp, otpTtlSeconds);
        }

        return otp;
    }

    // OTP verify karo — match hone par Redis se OTP + cooldown delete
    public boolean verify(String email, String otp) {
        if (otp == null || otp.isBlank()) {
            return false;
        }
        String cleanOtp = otp.trim();

        // Brute-force protection — max attempts cross hone par OTP invalid
        if (!hasAttemptsLeft(email)) {
            return false;
        }

        // NOTE: Pehle yahan ek "123456" universal fallback tha (dev testing ke liye).
        // Ab usse HATA diya gaya hai — koi bhi fixed code kabhi accept nahi hota.
        // Reason: ye ek full account-takeover vector tha (kisi ka email + ye code
        // = uska account). Dev testing ke liye ab `app.otp.log-otp` kaafi hai —
        // real (random) OTP console me dikhta hai.
        String stored = stringRedisTemplate.opsForValue().get(emailKey(email));
        if (stored != null && stored.equals(cleanOtp)) {
            clearAttempts(email);
            clear(email);
            return true;
        }

        registerFailedAttempt(email);
        return false;
    }

    // Kitne attempts bache hain? (max 5 failed tries ke baad OTP lock)
    private boolean hasAttemptsLeft(String email) {
        String attempts = stringRedisTemplate.opsForValue().get(attemptsKey(email));
        if (attempts == null) {
            return true;
        }
        try {
            return Integer.parseInt(attempts) < MAX_OTP_ATTEMPTS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private void registerFailedAttempt(String email) {
        String key = attemptsKey(email);
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
        stringRedisTemplate.opsForValue().set(key, String.valueOf(attempts), Duration.ofSeconds(otpTtlSeconds));
    }

    private void clearAttempts(String email) {
        stringRedisTemplate.delete(attemptsKey(email));
    }

    // Kya abhi resend kar sakte hain? (cooldown period khatam hua hai ya nahi)
    public boolean canResend(String email) {
        return Boolean.FALSE.equals(stringRedisTemplate.hasKey(cooldownKey(email)));
    }

    public void clear(String email) {
        stringRedisTemplate.delete(emailKey(email));
        stringRedisTemplate.delete(cooldownKey(email));
        stringRedisTemplate.delete(attemptsKey(email));
    }

    private String emailKey(String email) {
        return OTP_PREFIX + email.toLowerCase();
    }

    private String cooldownKey(String email) {
        return OTP_COOLDOWN_PREFIX + email.toLowerCase();
    }

    private String attemptsKey(String email) {
        return OTP_ATTEMPTS_PREFIX + email.toLowerCase();
    }
}