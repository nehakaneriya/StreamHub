package com.neha.StreamHub.entities;


import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens",indexes = {
        @Index(name = "refresh_token_jti_idx",columnList = "jti",unique = true),
        @Index(name = "refresh_token_user_id_idx",columnList = "user_id")
})
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(name = "jti",unique = true,nullable = false,updatable = false)
        private String jti;

        @ManyToOne(optional = false,fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id",nullable = false,updatable = false)
        private User user;

        @Column(nullable = false)
        private Instant expiresAt;

        @Column(nullable = false)
        private boolean revoked;

        private String replacedByToken;

}
