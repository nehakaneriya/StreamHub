package com.neha.StreamHub.security;


import com.neha.StreamHub.cache.JwtBlacklistService;
import com.neha.StreamHub.cache.UserCacheService;
import com.neha.StreamHub.entities.User;
import com.neha.StreamHub.helper.UserHelper;
import com.neha.StreamHub.redis.UserKeyGenerator;
import com.neha.StreamHub.repositories.UserRepository;
import io.jsonwebtoken.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final JwtBlacklistService jwtBlacklistService;
    private final UserCacheService userCacheService;
    private final Logger logger= LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String header=request.getHeader("Authorization");

        if(header!=null && header.startsWith("Bearer ")){

            String token = header.substring(7);

            try{

                // 1. Check Redis Blacklist (Logout status check)
                if (jwtBlacklistService.isTokenBlacklisted(token)) {
                    // Raw token log mat karo — sirf fingerprint
                    logger.warn("[JWT-AUTH][BLACKLISTED-TOKEN] Blacklisted Token Attempted: tokenFp={}",
                            UserKeyGenerator.tokenFingerprint(token));
                    request.setAttribute("error", "Token is revoked or user logged out");
                    filterChain.doFilter(request, response);
                    return;
                }

                // Parse once, check type from same result — avoid double parse
                Jws<Claims> parse = jwtService.parse(token);
                Claims payload = parse.getPayload();

                if (!"access".equals(payload.get("typ"))) {
                    request.setAttribute("error", "Invalid token type: expected access token");
                    filterChain.doFilter(request, response);
                    return;
                }

                String userId = payload.getSubject();
                UUID userUuid = UserHelper.parseUUID(userId);

                // 3. Fetch User with Redis Caching Layer (DB Query bachti hai)
                User user = userCacheService.getCachedUser(userId);

                if (user == null) {
                    var userOpt = userRepository.findById(userUuid);

                    if (userOpt.isEmpty()) {
                        request.setAttribute("error", "User no longer exists");
                        filterChain.doFilter(request, response);
                        return;
                    }

                    user = userOpt.get();
                    userCacheService.cacheUser(userId, user); // Cache in Redis
                    logger.info("[JWT-AUTH] User loaded from DB & cached. userId={}", userId);
                } else {
                    logger.info("[JWT-AUTH] User loaded from Redis cache. userId={}", userId);
                }

                if (!user.isEnable()) {
                    request.setAttribute("error", "User account is disabled");
                    filterChain.doFilter(request, response);
                    return;
                }

                // User valid hai — authentication set karo
                List<GrantedAuthority> authorities = user.getRoles() == null ? List.of() :
                        user.getRoles().stream()
                                .map(role -> new SimpleGrantedAuthority(role.getName()))
                                .collect(Collectors.toList());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                if (SecurityContextHolder.getContext().getAuthentication() == null)
                    SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                logger.error("[JWT-AUTH][ERR-JWT-AUTH-001] JWT Token Expired: {}", e.getMessage());
                request.setAttribute("error", "Token Expired");
            } catch (MalformedJwtException e){
                logger.error("[JWT-AUTH][ERR-JWT-AUTH-002] Invalid JWT Token: {}", e.getMessage());
                request.setAttribute("error", "Invalid Token Format");

            } catch (Exception e){
                logger.error("[JWT-AUTH][ERR-JWT-AUTH-003] Authentication Error: {}", e.getMessage(), e);
                request.setAttribute("error", "Authentication Failed");


            }
        }
        filterChain.doFilter(request,response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Context path hatao — warna servlet context configure karne par path match nahi hota
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }

        // /auth/me protected endpoint hai — isko filter karna hai
        if (uri.equals("/api/v1/auth/me")) {
            return false;
        }
        // Path SEGMENT match — pehle `startsWith("/api/v1/auth")` tha jo
        // "/api/v1/authorization..." jaise future paths ko bhi match kar deta tha
        return uri.equals("/api/v1/auth") || uri.startsWith("/api/v1/auth/");
    }
}
