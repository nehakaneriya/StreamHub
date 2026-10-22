package com.neha.StreamHub.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

@Entity
@Table(name="videos",
        indexes = {
                @Index(name = "idx_video_title", columnList = "title"),
                @Index(name = "idx_video_user", columnList = "user_id"),
                @Index(name = "idx_video_created", columnList = "createdAt"),
                @Index(name = "idx_video_content_type", columnList = "contentType")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Video extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String videoId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false, length = 50, columnDefinition = "varchar(50) default 'other'")
    private String category = "other";

    @Column(nullable = false)
    private String filePath;

    // total unique views count (denormalized)
    @Column(nullable = false)
    private long viewCount = 0;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;
}