package com.neha.StreamHub.controllers;

import com.neha.StreamHub.dtos.response.FeedbackDto;
import com.neha.StreamHub.services.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    // ── USER: Feedback submit karo ────────────────────────────────────────────
    // POST /api/v1/feedback
    @PostMapping("/api/v1/feedback")
    public ResponseEntity<FeedbackDto> submitFeedback(
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String message = body.get("message");
        String userEmail = authentication.getName();

        FeedbackDto saved = feedbackService.submitFeedback(message, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ── ADMIN: Saare feedbacks dekho ─────────────────────────────────────────
    // GET /api/v1/admin/feedbacks
    @GetMapping("/api/v1/admin/feedbacks")
    public ResponseEntity<List<FeedbackDto>> getAllFeedbacks() {
        return ResponseEntity.ok(feedbackService.getAllFeedbacks());
    }

    // ── ADMIN: Ek feedback delete karo ───────────────────────────────────────
    // DELETE /api/v1/admin/feedbacks/{id}
    @DeleteMapping("/api/v1/admin/feedbacks/{id}")
    public ResponseEntity<Map<String, String>> deleteFeedback(@PathVariable String id) {
        feedbackService.deleteFeedback(id);
        return ResponseEntity.ok(Map.of("message", "Feedback deleted successfully"));
    }
}
