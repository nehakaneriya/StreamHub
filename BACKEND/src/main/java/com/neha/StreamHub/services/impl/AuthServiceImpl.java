package com.neha.StreamHub.services.impl;

import com.neha.StreamHub.cache.UserCacheService;
import com.neha.StreamHub.dtos.common.UserDto;
import com.neha.StreamHub.entities.User;
import com.neha.StreamHub.exception.BadRequestException;
import com.neha.StreamHub.repositories.UserRepository;
import com.neha.StreamHub.services.AuthService;
import com.neha.StreamHub.services.MailService;
import com.neha.StreamHub.services.OtpService;
import com.neha.StreamHub.services.UserService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@AllArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final OtpService otpService;
    private final MailService mailService;
    private final UserRepository userRepository;
    private final UserCacheService userCacheService;

    @Override
    public UserDto registerUser(UserDto userDto) {

        // User enable=false se create hota hai (email verify hone tak login nahi kar sakta)
        UserDto created = userService.createPendingUser(userDto);

        // OTP generate karo, Redis me store karo, email par bhejo
        // NOTE: Mail fail hone par exception propagate hota hai (pehle silently
        // log hota tha) — taaki user ko pata chale email nahi gaya.
        String otp = otpService.generateAndStore(created.getEmail());
        try {
            mailService.sendOtpEmail(created.getEmail(), created.getName(), otp);
        } catch (Exception e) {
            // OTP Redis me pada reh jayega — use hata do warna stale OTP rahega
            otpService.clear(created.getEmail());
            throw e;
        }

        return created;
    }

    @Override
    public void cancelRegistration(String email, String otp) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required");
        }
        if (otp == null || otp.isBlank()) {
            throw new BadRequestException("OTP is required");
        }

        String cleanEmail = email.trim();

        // SECURITY: Pehle koi bhi unauthenticated caller kisi bhi email ka
        // pending account delete kar sakta tha (DoS + enumeration).
        // Ab OTP verify karna zaroori hai — OTP usi email par bheja gaya tha,
        // to ownership proof hai.
        if (!otpService.verify(cleanEmail, otp)) {
            throw new BadRequestException("Invalid or expired OTP. Please try again.");
        }

        userRepository.findByEmailIgnoreCase(cleanEmail)
                .ifPresent(user -> {
                    if (user.isEnable()) {
                        throw new BadRequestException("Cannot cancel registration for an already verified user");
                    }
                    userRepository.delete(user);
                    if (user.getId() != null) {
                        userCacheService.evictUserCache(user.getId().toString());
                    }
                });
    }
}
