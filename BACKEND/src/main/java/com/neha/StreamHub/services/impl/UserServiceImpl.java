package com.neha.StreamHub.services.impl;

import com.neha.StreamHub.cache.UserCacheService;
import com.neha.StreamHub.config.AppConstants;
import com.neha.StreamHub.dtos.common.UserDto;
import com.neha.StreamHub.entities.Provider;
import com.neha.StreamHub.entities.Role;
import com.neha.StreamHub.entities.User;
import com.neha.StreamHub.entities.Video;
import com.neha.StreamHub.exception.ResourceNotFoundException;
import com.neha.StreamHub.helper.UserHelper;
import com.neha.StreamHub.repositories.RefreshTokenRepository;
import com.neha.StreamHub.repositories.RoleRepository;
import com.neha.StreamHub.repositories.UserRepository;
import com.neha.StreamHub.services.UserService;
import com.neha.StreamHub.services.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

   // User listing ke liye max rows (memory exhaustion se bachao)
   private static final int MAX_USER_LIST_SIZE = 1000;

   private final UserRepository userRepository;
   private final ModelMapper modelMapper;
   private final RoleRepository roleRepository;
   private final PasswordEncoder passwordEncoder;
   private final UserCacheService userCacheService;
   private final RefreshTokenRepository refreshTokenRepository;
   private final VideoService videoService;

    // Password policy — min 6 chars, at least one letter, one number, one special symbol
    private void validatePassword(String password) {
        if (password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        if (!password.matches(".*[A-Za-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must contain at least one number");
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalArgumentException("Password must contain at least one special character");
        }
    }


    @Override
    public UserDto createUser(UserDto userDto) {
        return createUserInternal(userDto, true);
    }

    @Override
    public UserDto createPendingUser(UserDto userDto) {
        return createUserInternal(userDto, false);
    }

    private UserDto createUserInternal(UserDto userDto, boolean enable) {
        if(userDto.getEmail()==null || userDto.getEmail().isBlank()){
            throw new IllegalArgumentException("Email is Required");
        }
        if(userDto.getPassword()==null || userDto.getPassword().isBlank()){
            throw new IllegalArgumentException("Password is Required");
        }

        // Strong password enforcement (registration)
        validatePassword(userDto.getPassword());

        // Email hamesha lowercase normalize karo — warna "A@x.com" aur "a@x.com"
        // do alag accounts ban jate the aur login lookup miss hota tha
        String cleanEmail = userDto.getEmail().trim().toLowerCase();
        Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(cleanEmail);

        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            if (existingUser.isEnable()) {
                throw new IllegalArgumentException("Email already exists");
            }
            // If the user was pending verification, update details with new registration info
            existingUser.setName(userDto.getName());
            existingUser.setPassword(passwordEncoder.encode(userDto.getPassword()));
            existingUser.setProvider(userDto.getProvider() != null ? userDto.getProvider() : Provider.LOCAL);
            existingUser.setEnable(enable);
            User savedUser = userRepository.save(existingUser);
            return modelMapper.map(savedUser, UserDto.class);
        }


        User user = modelMapper.map(userDto, User.class);
        user.setEmail(cleanEmail);
        user.setProvider(userDto.getProvider()!=null ? userDto.getProvider() : Provider.LOCAL);

        user.setPassword(
                passwordEncoder.encode(userDto.getPassword())
        );
        // Email verification ke liye account initially disabled rahega —
        // OTP verify hone par enable=true ho jayega (AuthController.verifyOtp)
        user.setEnable(enable);
        // assign the default role
        Role role = roleRepository
                .findByName("ROLE_" + AppConstants.USER_ROLE)
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        user.getRoles().add(role);
        User savedUser = userRepository.save(user);
        return modelMapper.map(savedUser,UserDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public Iterable<UserDto> getAllUsers() {

        // Bounded — unbounded findAll() poori user table memory me load karta tha
        return userRepository.findAll(PageRequest.of(0, MAX_USER_LIST_SIZE))
                .getContent()
                .stream()
                .map(user -> modelMapper.map(user, UserDto.class))
                .toList();
    }

    @Override
    public UserDto getUserByEmail(String email) {

        User user = userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given email id"));

        return modelMapper.map(user,UserDto.class);
    }

    @Override
    public UserDto getUserById(String userId) {
        User user = userRepository.findById(UserHelper.parseUUID(userId)).orElseThrow(() -> new ResourceNotFoundException("User not found with the given id"));

        return modelMapper.map(user,UserDto.class);
    }

    @Override
    public UserDto updateUser(UserDto userDto, String userId) {
        UUID uId = UserHelper.parseUUID(userId);
        User existingUser=userRepository
                .findById(uId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with the given id"));

        // Name update
        if (userDto.getName() != null && !userDto.getName().isBlank()) {
            existingUser.setName(userDto.getName().trim());
        }

        if(userDto.getPassword()!=null && !userDto.getPassword().isBlank()) {
            // Password change me current password verify zaroori hai —
            // warna koi bhi hijacked session account permanently lock kar sakta tha
            if (userDto.getCurrentPassword() == null || userDto.getCurrentPassword().isBlank()) {
                throw new IllegalArgumentException("Current password is required to change the password");
            }
            if (!passwordEncoder.matches(userDto.getCurrentPassword(), existingUser.getPassword())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            // Naye password par bhi wahi policy lage jo registration par lagti hai
            validatePassword(userDto.getPassword());
            if (passwordEncoder.matches(userDto.getPassword(), existingUser.getPassword())) {
                throw new IllegalArgumentException("New password must be different from the current password");
            }
            existingUser.setPassword(passwordEncoder.encode(userDto.getPassword()));
            // Password change = saare refresh tokens revoke (baaki devices logout)
            refreshTokenRepository.deleteByUser(existingUser);
        }

        existingUser.setUpdatedAt(Instant.now());
        User updatedUser = userRepository.save(existingUser);

        userCacheService.evictUserCache(updatedUser.getId().toString());

        return modelMapper.map(updatedUser,UserDto.class);
    }

    @Override
    @Transactional
    public void deleteUser(String userId) {

        UUID uId = UserHelper.parseUUID(userId);
        User user = userRepository.findById(uId).orElseThrow(() -> new ResourceNotFoundException("User not found with the given id"));

        // Refresh tokens delete karo — nahi to deleted user apna refresh token
        // use karke unlimited naye access tokens banata rhta
        refreshTokenRepository.deleteByUser(user);

        // Cache evict karo — nahi to deleted user 60 min tak authenticate hota rhta
        userCacheService.evictUserCache(userId);

        // User ke videos ki files disk se bhi hatao (DB cascade sirf rows delete karta hai)
        if (user.getVideos() != null) {
            for (Video video : new ArrayList<>(user.getVideos())) {
                try {
                    videoService.delete(video.getVideoId());
                } catch (Exception e) {
                    log.warn("Could not delete video files for videoId: {} — {}", video.getVideoId(), e.getMessage());
                }
            }
        }

        userRepository.delete(user);
    }
}
