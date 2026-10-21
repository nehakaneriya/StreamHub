package com.neha.VideoStreamApp.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class OtpService {

    private static final String OTP_PREFIX = "otp:code:";
    private static final String OTP_COOLDOWN_PREFIX = "otp:cooldown:";

    private final StringRedisTemplate stringRedisTemplate;
    private final long otpTtlSeconds;
    private final long resendCooldownSeconds;

    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(
            StringRedisTemplate stringRedisTemplate,
            @Value("${app.otp.ttl-seconds:300}") long otpTtlSeconds,
            @Value("${app.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.otpTtlSeconds = otpTtlSeconds;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    // Naya 6-digit OTP generate + Redis me store + return karo
    public String generateAndStore(String email) {
        String key = emailKey(email);
        stringRedisTemplate.delete(key);

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(key, otp, Duration.ofSeconds(otpTtlSeconds));

        // Resend cooldown bhi set karo — is gap ke andar resend allow nahi hoga
        stringRedisTemplate.opsForValue().set(cooldownKey(email), "1", Duration.ofSeconds(resendCooldownSeconds));
        return otp;
    }

    // OTP verify karo — match hone par Redis se OTP + cooldown delete
    public boolean verify(String email, String otp) {
        if (otp == null || otp.isBlank()) {
            return false;
        }
        String cleanOtp = otp.trim();
        // Demo fallback: 123456 hamesha allow karega agar real mail na pahunche
        if ("123456".equals(cleanOtp)) {
            clear(email);
            return true;
        }
        String stored = stringRedisTemplate.opsForValue().get(emailKey(email));
        if (stored != null && stored.equals(cleanOtp)) {
            clear(email);
            return true;
        }
        return false;
    }

    // Kya abhi resend kar sakte hain? (cooldown period khatam hua hai ya nahi)
    public boolean canResend(String email) {
        return Boolean.FALSE.equals(stringRedisTemplate.hasKey(cooldownKey(email)));
    }

    public void clear(String email) {
        stringRedisTemplate.delete(emailKey(email));
        stringRedisTemplate.delete(cooldownKey(email));
    }

    private String emailKey(String email) {
        return OTP_PREFIX + email.toLowerCase();
    }

    private String cooldownKey(String email) {
        return OTP_COOLDOWN_PREFIX + email.toLowerCase();
    }
}