package com.neha.StreamHub.controllers;

import com.neha.StreamHub.config.AppConstants;
import com.neha.StreamHub.dtos.request.CommentRequest;
import com.neha.StreamHub.dtos.response.CommentDto;
import com.neha.StreamHub.services.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // ── USER: Video par comment post karo ──────────────────────────────────
    // POST /api/v1/videos/{videoId}/comments
    @PostMapping("/api/v1/videos/{videoId}/comments")
    public ResponseEntity<?> addComment(
            @PathVariable String videoId,
            @RequestBody CommentRequest request,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String userEmail = authentication.getName();
        CommentDto saved = commentService.addComment(videoId, request.text(), userEmail, request.parentCommentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ── PUBLIC: Ek video ke saare comments dekho ────────────────────────────
    // GET /api/v1/videos/{videoId}/comments
    @GetMapping("/api/v1/videos/{videoId}/comments")
    public ResponseEntity<List<CommentDto>> getComments(@PathVariable String videoId) {
        return ResponseEntity.ok(commentService.getCommentsByVideo(videoId));
    }

    // ── USER/ADMIN: Comment delete karo (sirf apna, ya admin kisi ka bhi) ──
    // DELETE /api/v1/comments/{commentId}
    @DeleteMapping("/api/v1/comments/{commentId}")
    public ResponseEntity<?> deleteComment(
            @PathVariable String commentId,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String requesterEmail = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_" + AppConstants.ADMIN_ROLE));

        boolean deleted = commentService.deleteComment(commentId, requesterEmail, isAdmin);

        if (!deleted) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body("You are not allowed to delete this comment");
        }

        return ResponseEntity.ok("Comment deleted successfully");
    }

    // ── ADMIN: Comment hide karo (delete nahi, bas public view se hatao) ──
    // PATCH /api/v1/admin/comments/{commentId}/hide
    @PatchMapping("/api/v1/admin/comments/{commentId}/hide")
    public ResponseEntity<?> hideComment(
            @PathVariable String commentId,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_" + AppConstants.ADMIN_ROLE));
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        return ResponseEntity.ok(commentService.hideComment(commentId));
    }

    // ── ADMIN: Hidden comment wapas unhide karo ────────────────────────────
    // PATCH /api/v1/admin/comments/{commentId}/unhide
    @PatchMapping("/api/v1/admin/comments/{commentId}/unhide")
    public ResponseEntity<?> unhideComment(
            @PathVariable String commentId,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_" + AppConstants.ADMIN_ROLE));
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        return ResponseEntity.ok(commentService.unhideComment(commentId));
    }
}
