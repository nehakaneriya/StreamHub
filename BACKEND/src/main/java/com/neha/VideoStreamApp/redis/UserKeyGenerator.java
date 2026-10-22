package com.neha.VideoStreamApp.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class UserKeyGenerator {

    private static final String USER_CACHE_PREFIX = "user:details:";
    private static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:";

    public static String getUserCacheKey(String username) {
        return USER_CACHE_PREFIX + username;
    }

    /**
     * Blacklist key banata hai.
     *
     * SECURITY: Pehle raw JWT token hi key banaya jata tha — jisse Redis dump
     * ya key listing se live bearer tokens mil jate the. Ab token ka SHA-256
     * hash use hota hai — key se token recover nahi ho sakta, par blacklist
     * kaam waise hi karta hai.
     */
    public static String getJwtBlacklistKey(String token) {
        return JWT_BLACKLIST_PREFIX + sha256Hex(token);
    }

    /** Token ka safe, one-way fingerprint — logs me bhi yehi use karo. */
    public static String tokenFingerprint(String token) {
        if (token == null) {
            return "null";
        }
        return sha256Hex(token).substring(0, 16);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 JVM me hamesha available hai — but defensive handling
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
