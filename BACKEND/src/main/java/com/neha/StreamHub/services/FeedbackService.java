package com.neha.StreamHub.services;

import com.neha.StreamHub.dtos.response.FeedbackDto;

import java.util.List;

public interface FeedbackService {

    // User feedback submit kare
    FeedbackDto submitFeedback(String message, String userEmail);

    // Admin — saare feedbacks dekhe
    List<FeedbackDto> getAllFeedbacks();

    // Admin — ek feedback delete kare
    void deleteFeedback(String feedbackId);
}
