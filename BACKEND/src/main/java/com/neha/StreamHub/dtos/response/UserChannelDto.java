package com.neha.StreamHub.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserChannelDto {

    private String userId;
    private String userName;
    private long videoCount;
    private List<VideoDto> videos;
}