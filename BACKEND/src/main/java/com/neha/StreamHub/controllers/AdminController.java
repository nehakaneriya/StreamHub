package com.neha.StreamHub.controllers;

import com.neha.StreamHub.dtos.response.AdminDashboardDto;
import com.neha.StreamHub.dtos.common.UserDto;
import com.neha.StreamHub.dtos.response.CommentDto;
import com.neha.StreamHub.dtos.response.VideoDto;
import com.neha.StreamHub.entities.User;
import com.neha.StreamHub.exception.ResourceNotFoundException;
import com.neha.StreamHub.repositories.UserRepository;
import com.neha.StreamHub.services.AdminService;
import com.neha.StreamHub.services.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;
    private final CommentService commentService;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDto> getDashboardStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUserById(id));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(
            @PathVariable UUID id,
            Authentication authentication) {

        // Logged in admin dhundho
        User currentAdmin = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        // Apne aap ko delete karne se roko
        if (currentAdmin.getId().equals(id)) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "You cannot delete your own account");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        adminService.deleteUser(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "User deleted successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/videos")
    public ResponseEntity<List<VideoDto>> getAllVideos() {
        return ResponseEntity.ok(adminService.getAllVideos());
    }

    @DeleteMapping("/videos/{id}")
    public ResponseEntity<Map<String, String>> deleteVideo(@PathVariable("id") String id) {
        adminService.deleteVideo(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Video deleted successfully with ID: " + id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{id}/assign-admin")
    public ResponseEntity<Map<String, String>> assignAdminRole(@PathVariable UUID id) {
        adminService.assignAdminRole(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Admin role assigned successfully to user ID: " + id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/users/{id}/remove-admin")
    public ResponseEntity<Map<String, String>> removeAdminRole(
            @PathVariable UUID id,
            Authentication authentication) {

        // Logged in admin dhundho
        User currentAdmin = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        // Apna khud ka admin role hatane se roko — warna khud ko lock-out kar sakta hai
        if (currentAdmin.getId().equals(id)) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "You cannot remove your own admin role");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        adminService.removeAdminRole(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Admin role removed successfully from user ID: " + id);
        return ResponseEntity.ok(response);
    }

    // ── COMMENTS MODERATION ──────────────────────────────────────────────
    @GetMapping("/comments")
    public ResponseEntity<List<CommentDto>> getAllComments() {
        return ResponseEntity.ok(commentService.getAllComments());
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Map<String, String>> deleteComment(@PathVariable String id) {
        commentService.adminDeleteComment(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Comment deleted successfully");
        return ResponseEntity.ok(response);
    }
}
