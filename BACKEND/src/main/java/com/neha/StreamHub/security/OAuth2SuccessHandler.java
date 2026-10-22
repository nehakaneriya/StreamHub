package com.neha.StreamHub.security;

import com.neha.StreamHub.entities.Provider;
import com.neha.StreamHub.entities.RefreshToken;
import com.neha.StreamHub.entities.Role;
import com.neha.StreamHub.entities.User;
import com.neha.StreamHub.repositories.RefreshTokenRepository;
import com.neha.StreamHub.repositories.RoleRepository;
import com.neha.StreamHub.repositories.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final Logger logger= LoggerFactory.getLogger(this.getClass());
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final CookieService cookieService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleRepository roleRepository;

    @Value("${app.auth.frontend.success-redirect}")
    private String frontEndSuccessUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User=(OAuth2User)authentication.getPrincipal();

        String registrationId = "unknown";
        if (authentication instanceof OAuth2AuthenticationToken token){
            registrationId = token.getAuthorizedClientRegistrationId();
        }
        logger.info("OAuth2 login via: {}", registrationId);

        User user;
        switch (registrationId){
            case "google" -> {
                String googleId = oAuth2User.getAttributes().getOrDefault("sub", "").toString();
                String name = oAuth2User.getAttributes().getOrDefault("name", "").toString();
                Object rawEmail = oAuth2User.getAttributes().get("email");
                String email = (rawEmail != null) ? rawEmail.toString().trim().toLowerCase() : "";

                // Google hamesha verified email deta hai. Agar email nahi mila to
                // account banana hi nahi chahiye — warna "<name>@google.com" jaisa
                // fabricated email se account collision / takeover ho sakta hai.
                if (email.isBlank()) {
                    logger.warn("Google login me email nahi mila — account create nahi kiya");
                    response.sendRedirect(frontEndSuccessUrl + "?error=email_not_provided");
                    return;
                }
                if (googleId.isBlank()) {
                    logger.warn("Google login me 'sub' (provider id) nahi mila — account create nahi kiya");
                    response.sendRedirect(frontEndSuccessUrl + "?error=provider_id_missing");
                    return;
                }

                user = linkOrCreateOAuthUser(email, name, googleId, Provider.GOOGLE);
            }
            case "github"->{
                String name=oAuth2User.getAttributes().getOrDefault("login","").toString();
                String githubId=oAuth2User.getAttributes().getOrDefault("id","").toString();
                Object rawEmail = oAuth2User.getAttributes().get("email");
                String email = (rawEmail != null) ? rawEmail.toString().trim().toLowerCase() : "";

                // GitHub private email hone par email null aata hai. Tab hum
                // fabricated email use karte the — jo collision/takeover ka
                // source tha. Ab aisa nahi karte: email ke bina OAuth login fail.
                if (email.isBlank()) {
                    logger.warn("GitHub login me email nahi mila (private email?) — account create nahi kiya");
                    response.sendRedirect(frontEndSuccessUrl + "?error=email_not_provided");
                    return;
                }
                if (githubId.isBlank()) {
                    logger.warn("GitHub login me 'id' (provider id) nahi mila — account create nahi kiya");
                    response.sendRedirect(frontEndSuccessUrl + "?error=provider_id_missing");
                    return;
                }

                user = linkOrCreateOAuthUser(email, name, githubId, Provider.GITHUB);
            }
            default -> {
                throw new RuntimeException("Invalid Registration id");
            }
        }

        //username
        //user email
        //new usercreate
        //jwt token--token ke sath front --pe fir redirect.



        //refresh token bana ke dunga:
        String jti= UUID.randomUUID().toString();
        RefreshToken refreshTokenOb=RefreshToken.builder()
                .jti(jti)
                .user(user)
                .revoked(false)
                .expiresAt(Instant.now().plusSeconds(jwtService.getRefreshTtlSeconds()))
                .build();
        refreshTokenRepository.save(refreshTokenOb);
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user, refreshTokenOb.getJti());
        cookieService.attachRefreshCookie(response,refreshToken,(int)jwtService.getRefreshTtlSeconds());

        // Access token ko URL fragment me nahi bhejte (referrer leak risk).
        // Frontend /oauth/success page par redirect hota hai aur wahan
        // refresh cookie se hi naya access token fetch karta hai.
       // response.getWriter().write("login successful");
        response.sendRedirect(frontEndSuccessUrl);
    }

    /**
     * OAuth account ko existing user se link karo ya naya user banao.
     *
     * SECURITY: Pehle sirf email dekh kar existing account mila jata tha — bina
     * providerId compare kiye. Isse koi bhi OAuth account jiska email match karta
     * us existing account (ROLE_ADMIN bhi) ko hijack kar let tha.
     *
     * Ab sirf tab link hota hai jab:
     *   1) existing user ka provider SAME ho, AUR
     *   2) providerId SAME ho (matlab wahi OAuth identity)
     * Otherwise naya user banaya jata hai.
     */
    private User linkOrCreateOAuthUser(String email, String name, String providerId, Provider provider) {

        Role role = roleRepository
                .findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("ROLE_USER not found"));

        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (existing != null) {
            boolean sameProvider = existing.getProvider() == provider;
            boolean sameProviderId = providerId.equals(existing.getProviderId());

            if (sameProvider && sameProviderId) {
                // Wahi OAuth identity pe wapas aa raha hai — normal login
                existing.setName(name);
                return userRepository.save(existing);
            }

            if (sameProvider && !sameProviderId) {
                // Same provider, alag provider id → alag user hai jo same email
                // use kar raha hai. Existing account ko link NAHI karenge.
                logger.warn("OAuth login: email {} already linked to a different {} account (providerId mismatch) — "
                        + "creating a separate account", email, provider);
            } else {
                // Local/password account ya doosre provider ka account — link mat karo.
                // Pehle yahan account takeover ho sakta tha.
                logger.warn("OAuth login: email {} already exists with provider {} — not linking, "
                        + "creating a separate account", email, existing.getProvider());
            }
        }

        User newUser = User.builder()
                .email(email)
                .name(name)
                .enable(true)
                .provider(provider)
                .providerId(providerId)
                .build();
        newUser.getRoles().add(role);
        return userRepository.save(newUser);
    }
}
