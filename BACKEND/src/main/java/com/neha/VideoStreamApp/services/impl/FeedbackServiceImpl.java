package com.neha.VideoStreamApp.services.impl;

import com.neha.VideoStreamApp.dtos.response.FeedbackDto;
import com.neha.VideoStreamApp.entities.Feedback;
import com.neha.VideoStreamApp.entities.User;
import com.neha.VideoStreamApp.exception.ResourceNotFoundException;
import com.neha.VideoStreamApp.repositories.FeedbackRepository;
import com.neha.VideoStreamApp.repositories.UserRepository;
import com.neha.VideoStreamApp.services.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    // Admin listing ke liye max rows
    private static final int MAX_ADMIN_LIST_SIZE = 1000;

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    @Override
    public FeedbackDto submitFeedback(String message, String userEmail) {

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Feedback message cannot be empty");
        }
        if (message.length() > 1000) {
            throw new IllegalArgumentException("Feedback too long (max 1000 characters)");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Feedback feedback = Feedback.builder()
                .message(message.trim())
                .user(user)
                .build();

        Feedback saved = feedbackRepository.save(feedback);
        return mapToDto(saved);
    }

    @Override
    public List<FeedbackDto> getAllFeedbacks() {
        // Bounded — unbounded findAll() memory exhaustion ka risk
        return feedbackRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, MAX_ADMIN_LIST_SIZE))
                .getContent()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteFeedback(String feedbackId) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found with id: " + feedbackId));
        feedbackRepository.delete(feedback);
    }

    private FeedbackDto mapToDto(Feedback feedback) {
        return FeedbackDto.builder()
                .id(feedback.getId())
                .message(feedback.getMessage())
                .createdAt(feedback.getCreatedAt())
                .userId(feedback.getUser().getId() != null ? feedback.getUser().getId().toString() : null)
                .userName(feedback.getUser().getName())
                .userEmail(feedback.getUser().getEmail())
                .build();
    }
}
