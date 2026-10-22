package com.neha.StreamHub.security;
import com.neha.StreamHub.entities.Role;
import com.neha.StreamHub.entities.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Getter
@Setter
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final String issuer;

    public JwtService(
            @Value("${spring.security.jwt.secret}")String secret,
            @Value("${spring.security.jwt.access-ttl-seconds}") long accessTtlSeconds,
                      @Value("${spring.security.jwt.refresh-ttl-seconds}") long refreshTtlSeconds,
                      @Value("${spring.security.jwt.issuer}") String issuer,
            @Value("${spring.security.jwt.allow-ephemeral-secret:false}") boolean allowEphemeralSecret) {

        String effectiveSecret = secret;
        if (effectiveSecret == null || effectiveSecret.isBlank()) {
            // Secret configure nahi hai. QA/Prod me app start nahi hona chahiye.
            if (!allowEphemeralSecret) {
                throw new IllegalStateException(
                        "JWT_SECRET is not configured. Set the JWT_SECRET environment variable "
                                + "(64+ random characters) before starting the application.");
            }
            // Sirf DEV me — ek random, per-startup secret. Hardcoded secret kabhi use nahi hota.
            // Iske side-effect: app restart pe saare purane tokens invalid ho jayenge.
            byte[] random = new byte[64];
            new java.security.SecureRandom().nextBytes(random);
            effectiveSecret = java.util.Base64.getEncoder().encodeToString(random);
            log.warn("JWT_SECRET is not configured — using an EPHEMERAL random secret for this run. "
                    + "All tokens will be invalidated on restart. Set JWT_SECRET for a stable environment.");
        }

        if (effectiveSecret.length() < 64) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 64 characters long (got " + effectiveSecret.length() + ")");
        }

        this.key= Keys.hmacShaKeyFor(effectiveSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.issuer = issuer;
    }

    // ================= ACCESS TOKEN =================
    public String generateAccessToken(User user) {

        Instant now=Instant.now();
        List<String> roles=user.getRoles() == null ? List.of() :
                user.getRoles().stream().map(Role::getName).toList();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .claims(Map.of(
                        "email",user.getEmail(),
                        "roles",roles,
                        "typ","access"
                ))
                .signWith(key,SignatureAlgorithm.HS512)
                .compact();
    }

    // ================= REFRESH TOKEN =================
    public String generateRefreshToken(User user,String jti) {

        Instant now=Instant.now();

        return Jwts.builder()
                .id(jti)
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshTtlSeconds)))
                .claim("typ","refresh")
                .signWith(key,SignatureAlgorithm.HS512)
                .compact();
    }

    // ================= PARSE =================
    public Jws<Claims> parse(String token){
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)   // issuer claim likhte hain to validate bhi karo
                .build()
                .parseSignedClaims(token);
    }


    // ================= CHECK TOKEN TYPE =================
    public boolean isAccessToken(String  token){
        Claims c=parse(token).getPayload();
        return "access".equals(c.get("typ"));
    }

    public boolean isRefreshToken(String  token){
        Claims c=parse(token).getPayload();
        return "refresh".equals(c.get("typ"));
    }

    // ================= GET DATA =================
    public  UUID getUserId(String  token){
        Claims c=parse(token).getPayload();
        return UUID.fromString(c.getSubject());
    }

    public String  getJti(String  token){
        return parse(token).getPayload().getId();
    }

    public List<String> getRoles(String  token){
        Claims c=parse(token).getPayload();
        return (List<String>) c.get("roles");
    }

    public String getEmail(String token){
        Claims c=parse(token).getPayload();
        return (String) c.get("email");
    }

    // ================= GET EXPIRATION FOR REDIS BLACKLIST =================
    public Date getExpirationDate(String token) {
        return parse(token).getPayload().getExpiration();
    }

    // ================= VALIDATE TOKEN =================
    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
