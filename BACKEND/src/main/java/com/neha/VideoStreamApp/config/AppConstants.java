package com.neha.VideoStreamApp.config;

public class AppConstants {

    // ─── PUBLIC: Bina login ke accessible ───────────────────────────────────
    public static final String[] AUTH_PUBLIC_URLS = {

            // Auth (login / register / token refresh / logout — token required nahi)
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/admin/login",
            "/api/v1/auth/admin/refresh",
            "/api/v1/auth/admin/logout",

            // Email verification (OTP) — bina login ke accessible
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/resend-otp",
            "/api/v1/auth/cancel-registration",

            // OAuth2 callbacks (Spring Security handle karta hai)
            "/oauth2/**",
            "/login/oauth2/**",

            // Swagger / API Docs (dev only — prod mein disable karna)
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",

            // Public categories list (filter chips + upload dropdown ke liye)
            "/api/v1/categories",

            // NOTE: Public VIDEO endpoints (feed, single video, HLS, stream) jaan-boojh kar
            // YAHAN nahi rakhe. Ye method-specific hain (sirf GET) aur SecurityConfig me
            // alag se define kiye gaye hain — taaki POST/PUT/DELETE video rules
            // shadow na ho (Spring Security first-match-wins hai).
    };

    // ─── COMMENTS: GET public hai, POST/DELETE SecurityConfig mein method-wise protected ───
    public static final String COMMENTS_GET_PATTERN = "/api/v1/videos/*/comments";

    // ─── ADMIN ONLY ──────────────────────────────────────────────────────────
    public static final String[] AUTH_ADMIN_URLS = {
            "/api/v1/admin/**",    // poora admin panel
    };

    public static final String ADMIN_ROLE = "ADMIN";
    public static final String USER_ROLE  = "USER";
}
