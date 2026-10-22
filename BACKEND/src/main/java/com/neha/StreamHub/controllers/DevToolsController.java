package com.neha.StreamHub.controllers;

import com.neha.StreamHub.services.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * DEV-ONLY helper — SMTP config verify karne ke liye.
 *
 * SECURITY: Ye controller sirf tab load hota hai jab `app.dev-tools.enabled=true`.
 * QA aur prod profiles me ye property FALSE hai — matlab production me ye
 * endpoint exist hi nahi karega (arbitrary email bhejne ka vector nahi banna chahiye).
 *
 * Use: GET /api/v1/dev/mail-test?email=you@example.com
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevToolsController {

    private final MailService mailService;

    @Value("${spring.mail.from:no-reply@streamhub.com}")
    private String from;

    @Value("${spring.mail.host:unknown}")
    private String host;

    @Value("${spring.mail.port:0}")
    private int port;

    @Value("${spring.mail.username:}")
    private String username;

    @GetMapping("/api/v1/dev/mail-test")
    public ResponseEntity<Map<String, Object>> testMail(@RequestParam String email) {
        // Test code sirf email me dikhane ke liye — ye KABHI Redis me store nahi
        // hota (otpService.generateAndStore call nahi hota), to isse kisi account
        // verify nahi kar sakte. Ye fixed code sirf mail pipeline test karne ke
        // liye hai, real OTP verification ke liye nahi.
        String testOtp = "000000";

        Map<String, Object> config = Map.of(
                "smtpHost", host,
                "smtpPort", port,
                "from", from,
                "usernameConfigured", !username.isBlank(),
                "target", email
        );

        try {
            mailService.sendOtpEmail(email, "Test User", testOtp);
            log.info("[DEV-TOOLS] Test mail sent to {}", email);
            return ResponseEntity.ok(Map.of(
                    "result", "SENT",
                    "message", "Check the inbox (and spam folder) of " + email,
                    "config", config
            ));
        } catch (Exception e) {
            log.error("[DEV-TOOLS] Test mail FAILED for {}: {}", email, e.getMessage(), e);

            // ROOT CAUSE dikhao — generic message ("could not send") se pata nahi
            // chalta ki kya galat hai. Nested cause ko unwrap karte hain.
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }

            return ResponseEntity.status(502).body(Map.of(
                    "result", "FAILED",
                    "error", e.getMessage(),
                    "rootCause", root.getClass().getSimpleName() + ": " + root.getMessage(),
                    "config", config
            ));
        }
    }
}
