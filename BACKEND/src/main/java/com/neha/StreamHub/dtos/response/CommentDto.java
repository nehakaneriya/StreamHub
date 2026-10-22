package com.neha.StreamHub.dtos.response;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDto {

    private String id;
    private String text;
    private Instant createdAt;
    private Instant updatedAt;

    private String videoId;
    private String videoTitle;

    // Commenter ki display info
    private String userId;
    private String userName;
    private String userEmail;

    // Agar ye ek reply hai, to parent comment ka ID (null = top-level comment)
    private String parentCommentId;

    // Is comment ke replies (sirf top-level comments ke liye bhara jata hai)
    @Builder.Default
    private List<CommentDto> replies = new ArrayList<>();

    // Admin dwara hide kiya gaya hai ya nahi
    private boolean hidden;
}
