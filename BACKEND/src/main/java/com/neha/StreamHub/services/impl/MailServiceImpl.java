package com.neha.StreamHub.services.impl;

import com.neha.StreamHub.exception.MailDeliveryException;
import com.neha.StreamHub.services.MailService;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:no-reply@streamhub.com}")
    private String from;

    @Value("${app.name:StreamHub}")
    private String appName;

    @Value("${app.otp.ttl-seconds:300}")
    private long otpTtlSeconds;

    /**
     * Gmail App Password me spaces aate hain ("abcd efgh ijkl mnop") — user
     * seedha paste kar deta hai. JavaMail un spaces ke saath auth karta hai aur
     * "Authentication failed" deta hai. Yahan trim kar dete hain.
     */
    @PostConstruct
    public void trimMailCredentials() {
        if (mailSender instanceof JavaMailSenderImpl impl) {
            String username = impl.getUsername();
            String password = impl.getPassword();
            if (username != null && !username.equals(username.trim())) {
                log.info("[MAIL] Trimmed whitespace from SMTP username");
                impl.setUsername(username.trim());
            }
            if (password != null && !password.equals(password.trim())) {
                log.info("[MAIL] Trimmed whitespace from SMTP password (Gmail app passwords me spaces aate hain)");
                impl.setPassword(password.trim());
            }
        }
    }

    @Override
    public void sendOtpEmail(String to, String name, String otp) {
        String displayName = (name == null || name.isBlank()) ? "there" : name;
        int ttlMinutes = (int) Math.max(1, otpTtlSeconds / 60);

        String html = String.format("""
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:24px;border:1px solid #e5e7eb;border-radius:16px;">
                  <h2 style="margin:0 0 8px;color:#111827;">Verify your email</h2>
                  <p style="color:#374151;font-size:15px;">Hi %s,</p>
                  <p style="color:#374151;font-size:15px;">Use the OTP below to complete your %s registration. It is valid for <strong>%d minutes</strong>.</p>
                  <div style="font-size:32px;font-weight:800;letter-spacing:8px;color:#dc2626;margin:24px 0;text-align:center;">%s</div>
                  <p style="color:#6b7280;font-size:13px;">If you didn't request this, you can ignore this email.</p>
                </div>
                """, displayName, appName, ttlMinutes, otp);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(appName + " — Verify your email");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("[MAIL] OTP sent to {}", to);
        } catch (Exception e) {
            // Pehle ye sirf log karta tha aur registration "successful" return kar deta tha —
            // user ko phir pata hi nahi chalta tha ki email gaya hi nahi, aur wo
            // "OTP has expired" pe atak jata tha. Ab error propagate karta hai taaki
            // frontend clearly bata sake "email nahi gaya, dobara try karo".
            log.error("[MAIL][ERR-MAIL-001] Could not send OTP to {} — from={} Reason: {}",
                    to, from, e.getMessage(), e);
            throw new MailDeliveryException(
                    "We could not send the verification email. Please check your address and try again.", e);
        }
    }
}