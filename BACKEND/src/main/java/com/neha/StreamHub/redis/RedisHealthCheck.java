package com.neha.StreamHub.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Redis connectivity check at startup.
 *
 * Ye is liye hai ki Redis band hone par problem SILENT na ho. Pehle Redis down
 * hone par:
 *   - OTP generate/verify 500 de raha tha (clear error nahi tha)
 *   - JWT blacklist check har authenticated request ko 401 kar raha tha
 *   - Rate limiter login block kar raha tha
 *   - User ko sirf "login nahi hua" dikhta tha, root cause pata hi nahi chalta tha
 *
 * Ab startup pe hi clearly log hota hai ki Redis available hai ya nahi.
 */
@Slf4j
@Component
public class RedisHealthCheck {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthCheck(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkRedis() {
        try {
            String pong = connectionFactory.getConnection().ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                log.info("[REDIS-HEALTH] Redis is reachable (PONG) — caching, OTP, rate limiting and JWT blacklist all active.");
            } else {
                log.warn("[REDIS-HEALTH] Redis reachable but unexpected ping response: {}", pong);
            }
        } catch (Exception e) {
            log.error("""

                    ╔══════════════════════════════════════════════════════════════════════╗
                    ║  REDIS IS NOT REACHABLE — degraded mode                                ║
                    ╠══════════════════════════════════════════════════════════════════════╣
                    ║  Isse:                                                              ║
                    ║   • Email OTP (registration / verification) NAHI chalega            ║
                    ║   • Rate limiting (brute-force protection) skip ho jayega          ║
                    ║   • Redis caching off rahegi (DB se direct chalega)                 ║
                    ║   • JWT blacklist check (logout) kaam nahi karega                  ║
                    ║                                                                      ║
                    ║  Fix: Redis service start karo —                                   ║
                    ║     Windows : services.msc → Redis → Start                          ║
                    ║     Docker  : docker compose up -d redis                            ║
                    ║  Reason: {}                                                        ║
                    ╚══════════════════════════════════════════════════════════════════════╝
                    """, e.getMessage());
        }
    }
}
