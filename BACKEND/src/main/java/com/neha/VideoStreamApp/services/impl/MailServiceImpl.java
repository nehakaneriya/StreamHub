package com.neha.VideoStreamApp.services.impl;

import com.neha.VideoStreamApp.services.MailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:no-reply@videostream.com}")
    private String from;

    @Override
    public void sendOtpEmail(String to, String name, String otp) {
        String displayName = (name == null || name.isBlank()) ? "there" : name;

        String html = String.format("""
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:24px;border:1px solid #e5e7eb;border-radius:16px;">
                  <h2 style="margin:0 0 8px;color:#111827;">Verify your email</h2>
                  <p style="color:#374151;font-size:15px;">Hi %s,</p>
                  <p style="color:#374151;font-size:15px;">Use the OTP below to complete your StreamHub registration. It is valid for <strong>5 minutes</strong>.</p>
                  <div style="font-size:32px;font-weight:800;letter-spacing:8px;color:#dc2626;margin:24px 0;text-align:center;">%s</div>
                  <p style="color:#6b7280;font-size:13px;">If you didn't request this, you can ignore this email.</p>
                </div>
                """, displayName, otp);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("StreamHub — Verify your email");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("[MAIL] OTP sent to {}", to);
        } catch (Exception e) {
            log.warn("[MAIL][WARN] Could not send OTP via SMTP ({}), fallback OTP for {}: {}", e.getMessage(), to, otp);
        }
    }
}