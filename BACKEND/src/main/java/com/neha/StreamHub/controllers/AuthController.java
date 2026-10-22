package com.neha.StreamHub.controllers;

import com.neha.StreamHub.cache.JwtBlacklistService;
import com.neha.StreamHub.cache.UserCacheService;
import com.neha.StreamHub.dtos.request.LoginRequest;
import com.neha.StreamHub.dtos.request.RefreshTokenRequest;
import com.neha.StreamHub.dtos.request.ResendOtpRequest;
import com.neha.StreamHub.dtos.request.VerifyOtpRequest;
import com.neha.StreamHub.dtos.response.TokenResponse;
import com.neha.StreamHub.dtos.common.UserDto;
import com.neha.StreamHub.entities.RefreshToken;
import com.neha.StreamHub.entities.User;
import com.neha.StreamHub.exception.BadRequestException;
import com.neha.StreamHub.exception.TooManyRequestsException;
import com.neha.StreamHub.repositories.RefreshTokenRepository;
import com.neha.StreamHub.repositories.UserRepository;
import com.neha.StreamHub.security.CookieService;
import com.neha.StreamHub.security.JwtService;
import com.neha.StreamHub.services.AuthService;
import com.neha.StreamHub.services.MailService;
import com.neha.StreamHub.services.OtpService;
import com.neha.StreamHub.services.RateLimitService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ModelMapper mapper;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CookieService cookieService;
    private final JwtBlacklistService jwtBlacklistService;
    private final OtpService otpService;
    private final MailService mailService;
    private final UserCacheService userCacheService;
    private final RateLimitService rateLimitService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {

        // Brute-force protection — 5 failed attempts ke baad account lock
        String identifier = loginRequest.email();
        if (rateLimitService.isLoginBlocked(identifier)) {
            throw new TooManyRequestsException("Too many failed login attempts. Please try again later.");
        }

        // authenticate
        Authentication authenticate = authenticate(loginRequest);
        User user = userRepository.findByEmailIgnoreCase(identifier).orElseThrow(() -> new BadCredentialsException("Invalid Username or Password"));
        if (!user.isEnable()) {
            throw new DisabledException("Please verify your email first");

        }

        // Login successful — failure counter reset
        rateLimitService.resetLoginFailures(identifier);

        String jti = UUID.randomUUID().toString();
        var refreshTokenOb = RefreshToken.builder()
                .jti(jti)
                .user(user)
                .expiresAt(Instant.now().plusSeconds(jwtService.getRefreshTtlSeconds()))
                .revoked(false)
                .build();

        //refresh token save--information
        refreshTokenRepository.save(refreshTokenOb);


        //access token--generate
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user, refreshTokenOb.getJti());

        // use cookie service to attach refresh token in cookie
        cookieService.attachRefreshCookie(response, refreshToken, (int) jwtService.getRefreshTtlSeconds());
        cookieService.addNoStoreHeaders(response);

        TokenResponse tokenResponse = TokenResponse.of(accessToken, null, jwtService.getAccessTtlSeconds(), mapper.map(user, UserDto.class));
        return ResponseEntity.ok(tokenResponse);

    }

    private Authentication authenticate(LoginRequest loginRequest) {
        try {

            return authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password()));

        } catch (DisabledException e) {
            // Unverified (email OTP) ya admin-disabled user — clear message do
            throw new DisabledException("Please verify your email first");
        } catch (Exception e) {
            // Failed attempt count karo — brute force protection
            rateLimitService.recordLoginFailure(loginRequest.email());
            throw new BadCredentialsException("Invalid Username or Password !!");
        }
    }


    //access and refresh token renew karne lie lie api
    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<TokenResponse> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletResponse response,
            HttpServletRequest request
    ) {


        //Thread.sleep(5000);

        String refreshToken = readRefreshTokenFromRequest(body, request).orElseThrow(() -> new BadCredentialsException("Refresh token is missing"));


        if(!jwtService.isRefreshToken(refreshToken)){
            throw new BadCredentialsException("Invalid Refresh Token Type");
        }

        String jti = jwtService.getJti(refreshToken);
        UUID userId = jwtService.getUserId(refreshToken);
        // Pessimistic lock ke saath — concurrent refresh me double-rotation nahi hoga
        RefreshToken storedRefreshToken = refreshTokenRepository.findByJtiForUpdate(jti)
                .orElseThrow(() -> new BadCredentialsException("Refresh token not recognized"));

        if(storedRefreshToken.isRevoked()){
            // SECURITY: Reuse detection — pehle revoked token reuse hone par sirf
            // 401 milta tha aur token family alive rehti thi. Ab poora family
            // revoke kar dete hain (stolen token ka signal).
            refreshTokenRepository.revokeAllForUser(storedRefreshToken.getUser().getId());
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        if(storedRefreshToken.getExpiresAt().isBefore(Instant.now())){
            throw new BadCredentialsException("Refresh token expired");
        }

        if(!storedRefreshToken.getUser().getId().equals(userId)){
            throw new BadCredentialsException("Refresh token does not belong to this user");
        }

        //refresh token ko rotate:
        storedRefreshToken.setRevoked(true);
        String newJti= UUID.randomUUID().toString();
        storedRefreshToken.setReplacedByToken(newJti);
        refreshTokenRepository.save(storedRefreshToken);

        // Fresh user DB se fetch karo — roles lazy load issue avoid karne ke liye
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        var newRefreshTokenOb = RefreshToken.builder()
                .jti(newJti)
                .user(user)
                .expiresAt(Instant.now().plusSeconds(jwtService.getRefreshTtlSeconds()))
                .revoked(false)
                .build();

        refreshTokenRepository.save(newRefreshTokenOb);
        String newAccessToken= jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user, newRefreshTokenOb.getJti());


        cookieService.attachRefreshCookie(response, newRefreshToken, (int) jwtService.getRefreshTtlSeconds());
        cookieService.addNoStoreHeaders(response);
        return ResponseEntity.ok(TokenResponse.of(newAccessToken, null, jwtService.getAccessTtlSeconds(), mapper.map(user, UserDto.class)));

    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // Access token ko Redis blacklist karo — logout ke baad wahi token invalid ho
        blacklistAccessToken(request);

        readRefreshTokenFromRequest(null, request).ifPresent(token -> {
            try {
                if (jwtService.isRefreshToken(token)) {
                    String jti = jwtService.getJti(token);
                    refreshTokenRepository.findByJti(jti).ifPresent(rt -> {
                        rt.setRevoked(true);
                        refreshTokenRepository.save(rt);
                    });
                }
            } catch (JwtException ignored) {
            }
        });

        // Use CookieUtil (same behavior)
        cookieService.clearRefreshCookie(response);
        cookieService.addNoStoreHeaders(response);
        SecurityContextHolder.clearContext();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }


    //this method will read refresh token from request header or body.
    private Optional<String> readRefreshTokenFromRequest(RefreshTokenRequest body, HttpServletRequest request) {
//            1. prefer reading refresh token from cookie
        if (request.getCookies() != null) {

            Optional<String> fromCookie = Arrays.stream(request.getCookies())
                    .filter(c -> cookieService.getRefreshTokenCookieName().equals(c.getName()))
                    .map(Cookie::getValue)
                    .filter(v -> !v.isBlank())
                    .findFirst();

            if (fromCookie.isPresent()) {
                return fromCookie;
            }
        }

        // 2 body:
        if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
            return Optional.of(body.refreshToken());
        }

        //3. custom header
        String refreshHeader = request.getHeader("X-Refresh-Token");
        if (refreshHeader != null && !refreshHeader.isBlank()) {
            return Optional.of(refreshHeader.trim());
        }

        //Authorization = Bearer <token>
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String candidate = authHeader.substring(7).trim();
            if (!candidate.isEmpty()) {
                try {
                    if (jwtService.isRefreshToken(candidate)) {
                        return Optional.of(candidate);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return Optional.empty();
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> registerUser(@RequestBody UserDto userDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerUser(userDto));
    }

    // Cancel Registration — OTP dale bina cancel karne par unverified pending user delete ho jaye
    // SECURITY: Ownership proof ke liye OTP zaroori hai — pehle koi bhi unauthenticated
    // caller kisi bhi email ka pending account delete kar sakta tha.
    @PostMapping("/cancel-registration")
    public ResponseEntity<Map<String, Object>> cancelRegistration(
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String otp
    ) {
        String targetEmail = email;
        String targetOtp = otp;
        if ((targetEmail == null || targetEmail.isBlank()) && body != null) {
            targetEmail = body.get("email");
            targetOtp = body.get("otp");
        }
        if (targetEmail == null || targetEmail.isBlank()) {
            throw new BadRequestException("Email is required to cancel registration");
        }
        if (targetOtp == null || targetOtp.isBlank()) {
            throw new BadRequestException("OTP is required to cancel registration");
        }

        authService.cancelRegistration(targetEmail, targetOtp);
        return ResponseEntity.ok(Map.of("message", "Registration cancelled successfully"));
    }

    // ===================== EMAIL VERIFICATION (OTP) =====================

    // OTP ko verify karo — match hone par user enable=true
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody VerifyOtpRequest req) {

        // Brute-force protection — OTP guess karke 10^6 combinations try na ho sakein
        if (rateLimitService.isOtpBlocked(req.email())) {
            throw new TooManyRequestsException("Too many verification attempts. Please request a new OTP.");
        }

        User user = userRepository.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new BadRequestException("Invalid or expired OTP. Please try again or resend."));

        if (user.isEnable()) {
            throw new BadRequestException("Invalid or expired OTP. Please try again or resend.");
        }

        if (!otpService.verify(req.email(), req.otp())) {
            rateLimitService.recordOtpFailure(req.email());
            throw new BadRequestException("Invalid or expired OTP. Please try again or resend.");
        }

        rateLimitService.resetOtpFailures(req.email());

        user.setEnable(true);
        userRepository.save(user);
        userCacheService.evictUserCache(user.getId().toString());

        return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now login."));
    }

    // Naya OTP bhejo (cooldown 60 sec ka hota hai)
    @PostMapping("/resend-otp")
    public ResponseEntity<Map<String, Object>> resendOtp(@RequestBody ResendOtpRequest req) {

        // Resend bhi limited — warna email spam / OTP flooding ho sakti hai
        if (rateLimitService.isResendBlocked(req.email())) {
            throw new TooManyRequestsException("Too many OTP requests. Please try again later.");
        }

        User user = userRepository.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new BadRequestException("If the account exists, a new OTP has been sent."));

        if (user.isEnable()) {
            throw new BadRequestException("If the account exists, a new OTP has been sent.");
        }

        if (!otpService.canResend(req.email())) {
            throw new BadRequestException("Please wait a moment before requesting a new OTP.");
        }

        String otp = otpService.generateAndStore(req.email());
        rateLimitService.recordResend(req.email());
        mailService.sendOtpEmail(req.email(), user.getName(), otp);

        return ResponseEntity.ok(Map.of("message", "A new OTP has been sent to your email."));
    }

    // Current logged-in user ki info return karo
    // Agar user delete ho gaya to JwtAuthenticationFilter 401 return karega
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        return ResponseEntity.ok(mapper.map(user, UserDto.class));
    }

    // ===================== ADMIN AUTH =====================
    @PostMapping("/admin/login")
    public ResponseEntity<TokenResponse> adminLogin(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {

        // Admin login par bhi brute-force protection
        if (rateLimitService.isLoginBlocked("admin:" + loginRequest.email())) {
            throw new TooManyRequestsException("Too many failed login attempts. Please try again later.");
        }

        Authentication authenticate = authenticate(loginRequest);
        User user = userRepository.findByEmailIgnoreCase(loginRequest.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!user.isEnable()) throw new DisabledException("User is disabled");

        // Sirf ADMIN allow karo
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_ADMIN"));
        // "not an admin" vs "wrong password" alag message se user enumeration hota tha
        if (!isAdmin) {
            rateLimitService.recordLoginFailure("admin:" + loginRequest.email());
            throw new BadCredentialsException("Invalid credentials");
        }

        rateLimitService.resetLoginFailures("admin:" + loginRequest.email());

        String jti = UUID.randomUUID().toString();
        var refreshTokenOb = RefreshToken.builder()
                .jti(jti)
                .user(user)
                .expiresAt(Instant.now().plusSeconds(jwtService.getRefreshTtlSeconds()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshTokenOb);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user, refreshTokenOb.getJti());

        // Admin ka ALAG cookie name
        cookieService.attachAdminRefreshCookie(response, refreshToken, (int) jwtService.getRefreshTtlSeconds());
        cookieService.addNoStoreHeaders(response);

        return ResponseEntity.ok(TokenResponse.of(accessToken, null, jwtService.getAccessTtlSeconds(), mapper.map(user, UserDto.class)));
    }

    @PostMapping("/admin/refresh")
    @Transactional
    public ResponseEntity<TokenResponse> adminRefreshToken(
            HttpServletResponse response,
            HttpServletRequest request
    ) {
        // Admin ka cookie name se token padho
        String refreshToken = readAdminRefreshTokenFromRequest(request)
                .orElseThrow(() -> new BadCredentialsException("Admin refresh token is missing"));

        if (!jwtService.isRefreshToken(refreshToken))
            throw new BadCredentialsException("Invalid refresh token type");

        String jti = jwtService.getJti(refreshToken);
        UUID userId = jwtService.getUserId(refreshToken);
        // Pessimistic lock ke saath — concurrent refresh me double-rotation nahi hoga
        RefreshToken stored = refreshTokenRepository.findByJtiForUpdate(jti)
                .orElseThrow(() -> new BadCredentialsException("Refresh token not recognized"));

        if (stored.isRevoked()) {
            // Reuse detection — poora family revoke
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId());
            throw new BadCredentialsException("Refresh token revoked");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) throw new BadCredentialsException("Refresh token expired");
        if (!stored.getUser().getId().equals(userId)) throw new BadCredentialsException("Token mismatch");

        // Fresh user DB se fetch karo — roles lazy load issue avoid karne ke liye
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // Extra check: sirf ADMIN ka token accept karo
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_ADMIN"));
        if (!isAdmin) throw new BadCredentialsException("Access denied: not an admin");

        // Rotate token
        stored.setRevoked(true);
        String newJti = UUID.randomUUID().toString();
        stored.setReplacedByToken(newJti);
        refreshTokenRepository.save(stored);

        var newRefreshTokenOb = RefreshToken.builder()
                .jti(newJti)
                .user(user)
                .expiresAt(Instant.now().plusSeconds(jwtService.getRefreshTtlSeconds()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(newRefreshTokenOb);

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user, newRefreshTokenOb.getJti());

        cookieService.attachAdminRefreshCookie(response, newRefreshToken, (int) jwtService.getRefreshTtlSeconds());
        cookieService.addNoStoreHeaders(response);

        return ResponseEntity.ok(TokenResponse.of(newAccessToken, null, jwtService.getAccessTtlSeconds(), mapper.map(user, UserDto.class)));
    }

    @PostMapping("/admin/logout")
    public ResponseEntity<Void> adminLogout(HttpServletRequest request, HttpServletResponse response) {
        // Admin access token bhi blacklist karo
        blacklistAccessToken(request);

        readAdminRefreshTokenFromRequest(request).ifPresent(token -> {
            try {
                if (jwtService.isRefreshToken(token)) {
                    String jti = jwtService.getJti(token);
                    refreshTokenRepository.findByJti(jti).ifPresent(rt -> {
                        rt.setRevoked(true);
                        refreshTokenRepository.save(rt);
                    });
                }
            } catch (JwtException ignored) {}
        });

        cookieService.clearAdminRefreshCookie(response);
        cookieService.addNoStoreHeaders(response);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // Admin cookie se token padhne ka helper
    private Optional<String> readAdminRefreshTokenFromRequest(HttpServletRequest request) {
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> cookieService.getAdminRefreshTokenCookieName().equals(c.getName()))
                    .map(Cookie::getValue)
                    .filter(v -> !v.isBlank())
                    .findFirst();
        }
        return Optional.empty();
    }

    // Authorization header se access token nikal kar Redis blacklist me daalo
    // TTL = token ki remaining validity — jab tak token expire hoga tab tak blocked rahega
    private void blacklistAccessToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return;
        }
        String candidate = authHeader.substring(7).trim();
        if (candidate.isEmpty()) {
            return;
        }
        try {
            if (jwtService.isAccessToken(candidate)) {
                long remainingMillis = jwtService.parse(candidate).getPayload().getExpiration().getTime() - System.currentTimeMillis();
                if (remainingMillis > 0) {
                    jwtBlacklistService.blacklistToken(candidate, remainingMillis);
                }
            }
        } catch (Exception ignored) {
            // Token invalid hai to blacklist karne ki zaroorat nahi
        }
    }
}
