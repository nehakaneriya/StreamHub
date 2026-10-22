package com.neha.StreamHub.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Health check endpoint.
 *
 * Production me ye zaruri hai:
 *   - Docker compose healthcheck (container state track karne ke liye)
 *   - Load balancer / uptime monitor (kya app zinda hai)
 *   - Deployment script (traffic bhejne se pehle wait karne ke liye)
 *
 * NOTE: Ye PUBLIC hai — koi sensitive data expose nahi karta, sirf "app alive hai"
 * batata hai. Isko authenticate karne ki zaroorat nahi (health probes me token
 * hota nahi).
 */
@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "StreamHub",
                "timestamp", Instant.now().toString()
        ));
    }
}
