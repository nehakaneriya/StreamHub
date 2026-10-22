package com.neha.StreamHub.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "video_views", indexes = {
        // Repeat-view check ke liye fast lookup — user+video pair
        @Index(name = "idx_view_video_user", columnList = "video_id, user_id"),
        @Index(name = "idx_view_video_ip", columnList = "video_id, viewer_ip")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    // login user ke liye; guest (bina login) hone par null rahega
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // guest viewers ke liye (login na hona) — based on IP
    @Column(length = 45)
    private String viewerIp;

    @Column(nullable = false, updatable = false)
    private Instant viewedAt;
}