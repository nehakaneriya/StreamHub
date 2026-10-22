package com.neha.StreamHub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neha.StreamHub.dtos.response.ApiError;
import com.neha.StreamHub.repositories.RoleRepository;
import com.neha.StreamHub.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationSuccessHandler successHandler;
    private final ObjectMapper objectMapper;

    @Value("${app.auth.frontend.failure-redirect}")
    private String frontEndFailureUrl;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, AuthenticationSuccessHandler successHandler, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.successHandler = successHandler;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // ── 1. PUBLIC (method-agnostic) ────────────────────────────────
                        .requestMatchers(AppConstants.AUTH_PUBLIC_URLS).permitAll()

                        // ── 1a. HEALTH CHECK ─────────────────────────────────────────────
                        // Public hona zaroori hai — Docker healthcheck aur load balancer
                        // ise bina token ke hit karte hain. Ye sirf "app alive hai"
                        // batata hai, koi sensitive data expose nahi karta.
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()

                        // ── 1b. DEV TOOLS ────────────────────────────────────────────────
                        // Dev-only helpers (jaise /api/v1/dev/mail-test).
                        // NOTE: Ye controller khud hi `app.dev-tools.enabled=true` par
                        // load hota hai — QA/Prod me bean create hi nahi hota, to
                        // endpoint wahan 404 dega (404, 401 nahi).
                        .requestMatchers("/api/v1/dev/**").permitAll()

                        // ── 1b. COMMENTS — GET public (dekhna), POST/DELETE login zaroori ──
                        .requestMatchers(HttpMethod.GET, AppConstants.COMMENTS_GET_PATTERN).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/videos/*/comments")
                        .hasAnyRole(AppConstants.USER_ROLE, AppConstants.ADMIN_ROLE)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/comments/*")
                        .hasAnyRole(AppConstants.USER_ROLE, AppConstants.ADMIN_ROLE)

                        // ── 2. AUTH → /auth/me ────────────────────────────────────────────
                        // Session verify endpoint — login hona zaroori
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me")
                        .hasAnyRole(AppConstants.USER_ROLE, AppConstants.ADMIN_ROLE)

                        // ── 3. ADMIN PANEL ────────────────────────────────────────────────
                        // /api/v1/admin/** — sirf ADMIN role
                        .requestMatchers(AppConstants.AUTH_ADMIN_URLS)
                        .hasRole(AppConstants.ADMIN_ROLE)

                        // ════════════════════════════════════════════════════════════════
                        // 4. VIDEO — ye rules GENERIC "/api/v1/videos/*" permitAll se
                        //    PEHLE aane CHAHIYE. Spring Security first-match-wins hai —
                        //    generic permitAll pehle aata tha to ye rules DEAD CODE the
                        //    aur upload/delete/update filter-chain pe open the.
                        // ════════════════════════════════════════════════════════════════

                        // 4a. MY VIDEOS — apne uploaded videos (specific rule, generic se pehle)
                        .requestMatchers(HttpMethod.GET, "/api/v1/videos/my-videos")
                        .hasAnyRole(AppConstants.USER_ROLE, AppConstants.ADMIN_ROLE)

                        // 4b. WRITE operations — login zaroori
                        .requestMatchers(HttpMethod.POST, "/api/v1/videos")
                        .hasAnyRole(AppConstants.USER_ROLE, AppConstants.ADMIN_ROLE)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/videos/**")
                        .hasAnyRole(AppConstants.USER_ROLE, AppConstants.ADMIN_ROLE)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/videos/**")
                        .hasAnyRole(AppConstants.USER_ROLE, AppConstants.ADMIN_ROLE)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/videos/**")
                        .hasAnyRole(AppConstants.USER_ROLE, AppConstants.ADMIN_ROLE)

                        // 4c. VIEW count — public hai (guest bhi video dekh sakta hai).
                        //     Controller khud 24h de-duplication aur rate-limit karta hai.
                        .requestMatchers(HttpMethod.POST, "/api/v1/videos/*/view")
                        .permitAll()

                        // 4d. PUBLIC video reads — sirf GET
                        .requestMatchers(HttpMethod.GET, "/api/v1/videos").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/videos/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/videos/user/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/videos/stream/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/videos/*/master.m3u8").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/videos/*/thumbnail.jpg").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/videos/*/*/playlist.m3u8").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/videos/*/*/*.ts").permitAll()

                        // ── 5. USER — EMAIL LOOKUP ────────────────────────────────────────
                        // /users/email/{email} — login zaroori
                        // NOTE: ye "/api/v1/users/*" se PEHLE aana chahiye ( niche dekho )
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/email/**")
                        .hasAnyRole(AppConstants.USER_ROLE, AppConstants.ADMIN_ROLE)

                        // ── 6. USER — LIST / BY-ID (sirf ADMIN) ──────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/v1/users", "/api/v1/users/*")
                        .hasRole(AppConstants.ADMIN_ROLE)

                        // ── 7. USER — UPDATE OWN PROFILE ─────────────────────────────────
                        // PUT /api/v1/users/{id} — Controller mein ownership check hai
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/**")
                        .hasAnyRole(AppConstants.USER_ROLE, AppConstants.ADMIN_ROLE)

                        // ── 8. USER — DELETE OWN ACCOUNT ─────────────────────────────────
                        // DELETE /api/v1/users/{id} — Controller mein ownership check hai
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**")
                        .hasAnyRole(AppConstants.USER_ROLE, AppConstants.ADMIN_ROLE)

                        // ── 9. FEEDBACK — USER SUBMIT ────────────────────────────────────
                        // POST /api/v1/feedback — login hona zaroori
                        .requestMatchers(HttpMethod.POST, "/api/v1/feedback")
                        .hasAnyRole(AppConstants.USER_ROLE, AppConstants.ADMIN_ROLE)

                        // ── 10. BAKI SAB — authenticated hona zaroori ────────────────────
                        .anyRequest().authenticated()
                )

                .oauth2Login(oauth2 -> oauth2
                        .successHandler(successHandler)
                        .failureHandler((req, res, ex) -> {
                            logger.error("OAuth2 login failed: {}", ex.getMessage());
                            // authorization_request_not_found — user ne back dabaya ya session expire
                            // Seedha login page pe bhejo with error message
                            res.sendRedirect(frontEndFailureUrl + "?error=" + ex.getMessage());
                        })
                        // Authorization request HttpSession me store hoti hai (default).
                        // Isliye session cookie (JSESSIONID) SameSite=Lax honi chahiye —
                        // Google se callback cross-site top-level GET hota hai, aur
                        // Strict me session cookie send nahi hoti (login fail).
                        .authorizationEndpoint(auth ->
                                auth.authorizationRequestRepository(
                                        new org.springframework.security.oauth2.client.web
                                                .HttpSessionOAuth2AuthorizationRequestRepository()
                                )
                        )
                )
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex->ex.authenticationEntryPoint((request, response, e ) -> {
                    //e.printStackTrace();
                    response.setStatus(401);
                    response.setContentType("application/json");

                    String message="Unauthorized Access !"+ e.getMessage();
                    String error= (String) request.getAttribute("error");
                    if (error!=null){
                        message=error;
                    }


                    var apiError= ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized Access",message,request.getRequestURI(),true);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(objectMapper.writeValueAsString(apiError));
                })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {

                           response.setStatus(403);
                           response.setContentType("application/json");
                           String message = accessDeniedException.getMessage();
                           String error =(String) request.getAttribute("error");
                           if (error!=null){
                               message =error;
                           }
                            var apiError = ApiError.of(
                                    HttpStatus.FORBIDDEN.value(),
                                    "Access Denied",
                                    accessDeniedException.getMessage(),
                                    request.getRequestURI(),
                                    true
                            );
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(apiError));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.front-end-url}") String corsUrls
    ){
        // SECURITY: Origins ko validate karo. Pehle koi bhi comma-separated
        // string seedha allowedOrigins me daal di jati thi — misconfiguration
        // (jaise "*" ya galat domain) ke saath allowCredentials(true) ka matlab
        // hai har authenticated endpoint cross-origin credential surface ban jata.
        List<String> origins = Arrays.stream(corsUrls.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .peek(origin -> {
                    if ("*".equals(origin)) {
                        throw new IllegalStateException(
                                "CORS origin '*' is not allowed when credentials are enabled. "
                                        + "Set app.cors.front-end-url to explicit origins.");
                    }
                    if (!origin.startsWith("http://") && !origin.startsWith("https://")) {
                        throw new IllegalStateException(
                                "Invalid CORS origin '" + origin + "' — must start with http:// or https://");
                    }
                })
                .toList();

        if (origins.isEmpty()) {
            throw new IllegalStateException(
                    "No CORS origins configured. Set app.cors.front-end-url (e.g. http://localhost:1304).");
        }

        var config=new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET","POST","DELETE","PUT","OPTIONS","PATCH","HEAD"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Refresh-Token", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",config);
        return source;
    }
}