package com.neha.StreamHub.dtos.response;

import lombok.*;
import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminDashboardDto {
    private long totalUsers;
    private long activeUsers;
    private long totalVideos;
    private long adminsCount;
    private Instant timestamp;
}
