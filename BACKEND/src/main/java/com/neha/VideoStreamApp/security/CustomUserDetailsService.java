package com.neha.VideoStreamApp.security;

import com.neha.VideoStreamApp.entities.User;
import com.neha.VideoStreamApp.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // Authentication ke liye DB se hi user lo — password chahiye, jo cache me nahi hota.
        // Redis cache se cached user (bina password ke) return karne par password check fail ho jata hai.
        // Email case-insensitive lookup — warna "A@x.com" aur "a@x.com" do alag account ban jate the.
        return userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password!!"));
    }
}