package com.neha.StreamHub.dtos.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.neha.StreamHub.entities.Provider;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDto {

    private UUID id;
    private String email;
    private String name;

    // Sirf incoming request (register/update) mein password read hoga,
    // response JSON mein kabhi serialize nahi hoga — hash leak prevent karta hai
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    // Password change karte waqt current password bhejna zaroori hai
    // (WRITE_ONLY — response me kabhi nahi jayega)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String currentPassword;

    @Builder.Default
    private boolean enable=true;

    private Instant createdAt;
    private Instant updatedAt;

    @Builder.Default
    private Provider provider=Provider.LOCAL;

    @Builder.Default
    private Set<RoleDto> roles=new HashSet<>();




}
