package com.neha.StreamHub.repositories;

import com.neha.StreamHub.entities.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, String> {

    // Method to find feedback by user ID, ordered by creation date in descending order
    List<Feedback> findByUserIdOrderByCreatedAtDesc(UUID userId);


    // Method to find all feedbacks ordered by creation date in descending order
    List<Feedback> findAllByOrderByCreatedAtDesc();

    // Paginated version (unbounded findAll memory exhaustion kar sakta tha)
    Page<Feedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
