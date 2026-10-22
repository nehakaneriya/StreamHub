package com.neha.VideoStreamApp.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VideoDto {

    private String videoId;
    private String title;
    private String description;
    private String contentType;
    private String category;

    /**
     * Server ka internal file path — KABHI client ko serialize nahi hona chahiye.
     * Pehle ye public endpoints par leak ho raha tha (server filesystem layout
     * + UUID→filename mapping expose).
     * @JsonIgnore sirf JSON serialization rokta hai — server side code (jo
     * VideoController.isFilePath() use karta hai) ko value milti rahegi.
     */
    @JsonIgnore
    private String filePath;

    // views
    private long viewCount;

    private Instant createdAt;
    private Instant updatedAt;

    // uploader details
    private String userId;
    private String userName;

    /**
     * Uploader ka email. Public (unauthenticated) feed/channel responses me
     * isko populate NAHI kiya jata — warna koi bhi visitor sabhi uploaders aur
     * commenters ke email addresses harvest kar sakta tha.
     * Admin panel ke liye populate hota hai.
     */
    private String userEmail;

    // admin panel ke liye — is video pe kitne comments hain
    private Long commentCount;
}
