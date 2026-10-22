package com.neha.StreamHub.services;

import com.neha.StreamHub.dtos.response.AdminDashboardDto;
import com.neha.StreamHub.dtos.common.UserDto;
import com.neha.StreamHub.dtos.response.CategoryStatsDto;
import com.neha.StreamHub.dtos.response.VideoDto;

import java.util.List;
import java.util.UUID;

public interface AdminService {
    AdminDashboardDto getDashboardStats();
    List<UserDto> getAllUsers();
    UserDto getUserById(UUID userId);
    void deleteUser(UUID userId);
    void deleteVideo(String videoId);
    List<VideoDto> getAllVideos();
    void assignAdminRole(UUID userId);

    // Admin role hatao — user wapas normal USER ban jayega
    void removeAdminRole(UUID userId);

    // Admin: har category me kitne videos hain
    List<CategoryStatsDto> getCategoryStats();
}
