package com.neha.StreamHub.dtos.common;
import lombok.*;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCacheDto implements Serializable {
    private UUID id;
    private String email;
    private String name;
    private boolean enable;
    private Set<String> roles;
}