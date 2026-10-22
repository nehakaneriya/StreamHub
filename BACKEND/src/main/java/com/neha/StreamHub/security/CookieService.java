package com.neha.StreamHub.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
@Getter
public class CookieService {

    private final String refreshTokenCookieName;
    private final String adminRefreshTokenCookieName;
    private final boolean  cookieHttpOnly;
    private final boolean  cookieSecure;
    private final String cookieDomain;
    private final String cookieSameSite;

    public CookieService(
            @Value("${spring.security.jwt.refresh-token-cookie-name}") String refreshTokenCookieName,
            @Value("${spring.security.jwt.admin-refresh-token-cookie-name}") String adminRefreshTokenCookieName,
            @Value("${spring.security.jwt.cookie-http-only}") boolean cookieHttpOnly,
            @Value("${spring.security.jwt.cookie-secure}") boolean cookieSecure,
            @Value("${spring.security.jwt.cookie-domain:}") String cookieDomain,
            @Value("${spring.security.jwt.cookie-same-site}")String cookieSameSite) {
        this.refreshTokenCookieName = refreshTokenCookieName;
        this.adminRefreshTokenCookieName = adminRefreshTokenCookieName;
        this.cookieHttpOnly = cookieHttpOnly;
        this.cookieSecure = cookieSecure;
        this.cookieDomain = cookieDomain;
        this.cookieSameSite = cookieSameSite;
    }

    // ===== USER COOKIE =====
    public void attachRefreshCookie(HttpServletResponse response, String value, int maxAge){
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(refreshTokenCookieName, value, maxAge).toString());
    }

    public void clearRefreshCookie(HttpServletResponse response){
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(refreshTokenCookieName, "", 0).toString());
    }

    // ===== ADMIN COOKIE =====
    public void attachAdminRefreshCookie(HttpServletResponse response, String value, int maxAge){
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(adminRefreshTokenCookieName, value, maxAge).toString());
    }

    public void clearAdminRefreshCookie(HttpServletResponse response){
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(adminRefreshTokenCookieName, "", 0).toString());
    }

    // ===== COMMON HELPER =====
    private ResponseCookie buildCookie(String name, String value, int maxAge) {
        var builder = ResponseCookie.from(name, value)
                .httpOnly(cookieHttpOnly)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAge)
                .sameSite(cookieSameSite);
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }
        return builder.build();
    }

    public void addNoStoreHeaders(HttpServletResponse response){
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        response.setHeader("Pragma","no-cache");
    }
}
