package com.neha.StreamHub.dtos.common;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class RoleDto {
    private UUID id;
    private String name;
}
