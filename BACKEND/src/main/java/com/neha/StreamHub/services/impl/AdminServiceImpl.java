package com.neha.StreamHub.services.impl;

import com.neha.StreamHub.config.AppConstants;
import com.neha.StreamHub.cache.UserCacheService;
import com.neha.StreamHub.dtos.response.AdminDashboardDto;
import com.neha.StreamHub.dtos.common.UserDto;
import com.neha.StreamHub.dtos.response.CategoryStatsDto;
import com.neha.StreamHub.dtos.response.VideoDto;
import com.neha.StreamHub.entities.Category;
import com.neha.StreamHub.entities.Role;
import com.neha.StreamHub.entities.User;
import com.neha.StreamHub.entities.Video;
import com.neha.StreamHub.exception.ResourceNotFoundException;
import com.neha.StreamHub.repositories.CategoryRepository;
import com.neha.StreamHub.repositories.CommentRepository;
import com.neha.StreamHub.repositories.RefreshTokenRepository;
import com.neha.StreamHub.repositories.RoleRepository;
import com.neha.StreamHub.repositories.UserRepository;
import com.neha.StreamHub.repositories.VideoRepository;
import com.neha.StreamHub.services.AdminService;
import com.neha.StreamHub.services.VideoService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    // Admin listing ke liye max rows (memory exhaustion se bachao)
    private static final int MAX_ADMIN_LIST_SIZE = 1000;

    private final Logger logger = LoggerFactory.getLogger(AdminServiceImpl.class);
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final RoleRepository roleRepository;
    private final ModelMapper modelMapper;
    private final VideoService videoService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CommentRepository commentRepository;
    private final CategoryRepository categoryRepository;
    private final UserCacheService userCacheService;

    @Override
    public AdminDashboardDto getDashboardStats() {

        // Counts DB side — pehle do baar findAll() call hota tha (poori table memory me)
        long totalUsers = userRepository.count();
        long totalVideos = videoRepository.count();
        long activeUsers = userRepository.countByEnableTrue();
        long adminsCount = userRepository.countByRoles_Name("ROLE_" + AppConstants.ADMIN_ROLE);

        return AdminDashboardDto.builder()
                .totalUsers(totalUsers)
                .totalVideos(totalVideos)
                .activeUsers(activeUsers)
                .adminsCount(adminsCount)
                .timestamp(Instant.now())
                .build();
    }

    @Override
    public List<UserDto> getAllUsers() {

        // Bounded — unbounded findAll() poori user table memory me load karta tha
        return userRepository.findAll(PageRequest.of(0, MAX_ADMIN_LIST_SIZE))
                .getContent()
                .stream()
                .map(user -> modelMapper.map(user, UserDto.class))
                .collect(Collectors.toList());
    }

    @Override
    public UserDto getUserById(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        return modelMapper.map(user, UserDto.class);
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        // Step 1: User ke saare videos ki files disk se delete karo
        // (videos/ folder + videos_hls/ folder dono)
        List<Video> userVideos = user.getVideos();
        if (userVideos != null && !userVideos.isEmpty()) {
            for (Video video : userVideos) {
                try {
                    videoService.delete(video.getVideoId());
                } catch (Exception e) {
                    logger.warn("Could not delete video files for videoId: {} — {}", video.getVideoId(), e.getMessage());
                }
            }
        }

        // Step 2: Refresh tokens delete karo
        refreshTokenRepository.deleteByUser(user);

        // Step 3: User cache evict karo — nahi to deleted user 60 min tak
        // cached credentials se authenticate hota rahega
        userCacheService.evictUserCache(userId.toString());

        // Step 4: User delete karo (JPA cascade baaki DB records hatayega)
        userRepository.delete(user);
    }

    @Override
    @Transactional
    public void assignAdminRole(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        Role adminRole = roleRepository
                .findByName("ROLE_" + AppConstants.ADMIN_ROLE)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Admin role not found")
                );

        if (!user.getRoles().contains(adminRole)) {
            user.getRoles().add(adminRole);
            userRepository.save(user);
            // Role cache evict — nahi to promote/demote 60 min tak effect na dikhe
            userCacheService.evictUserCache(userId.toString());
        }
    }

    @Override
    @Transactional
    public void removeAdminRole(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        Role adminRole = roleRepository
                .findByName("ROLE_" + AppConstants.ADMIN_ROLE)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Admin role not found")
                );

        user.getRoles().remove(adminRole);
        userRepository.save(user);
        // Role cache evict — demoted admin turant privileges lose kare
        userCacheService.evictUserCache(userId.toString());
    }

    @Override
    public List<VideoDto> getAllVideos() {

        // Bounded — unbounded findAll() poori video table memory me load karta tha
        List<VideoDto> videos = videoRepository.findAll(PageRequest.of(0, MAX_ADMIN_LIST_SIZE))
                .getContent()
                .stream()
                .map(this::mapToVideoDto)
                .toList();

        // Ek hi query me saare videos ke comment counts lo — [videoId, count] pairs
        Map<String, Long> commentCountMap = new HashMap<>();
        commentRepository.countGroupedByVideoId().forEach(row -> {
            commentCountMap.put((String) row[0], (Long) row[1]);
        });

        videos.forEach(video -> video.setCommentCount(commentCountMap.getOrDefault(video.getVideoId(), 0L)));
        return videos;
    }

    @Override
    public List<CategoryStatsDto> getCategoryStats() {

        // Har category slug ka video count — [category, count] pairs
        Map<String, Long> countBySlug = new HashMap<>();
        videoRepository.countGroupedByCategory().forEach(row -> {
            countBySlug.put((String) row[0], (Long) row[1]);
        });

        // Category names ke liye — categories table se lookup
        Map<String, String> nameBySlug = categoryRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Category::getSlug, Category::getName, (a, b) -> a));

        return countBySlug.entrySet().stream()
                .map(entry -> CategoryStatsDto.builder()
                        .slug(entry.getKey())
                        .name(nameBySlug.getOrDefault(entry.getKey(), entry.getKey()))
                        .videoCount(entry.getValue())
                        .build())
                .sorted((a, b) -> Long.compare(b.getVideoCount(), a.getVideoCount()))
                .toList();
    }

    @Override
    public void deleteVideo(String videoId) {
        videoService.delete(videoId);
    }

    private VideoDto mapToVideoDto(Video video) {

        return VideoDto.builder()
                .videoId(video.getVideoId())
                .title(video.getTitle())
                .description(video.getDescription())
                .contentType(video.getContentType())
                .category(video.getCategory())
                .filePath(video.getFilePath())
                .viewCount(video.getViewCount())
                .createdAt(video.getCreatedAt())
                .userId(
                        video.getUser() != null
                                ? video.getUser().getId().toString()
                                : null
                )
                .userName(
                        video.getUser() != null
                                ? video.getUser().getName()
                                : null
                )
                .userEmail(
                        video.getUser() != null
                                ? video.getUser().getEmail()
                                : null
                )
                .build();
    }
}