package com.neha.StreamHub.repositories;

import com.neha.StreamHub.entities.RefreshToken;
import com.neha.StreamHub.entities.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByJti(String jti);
    List<RefreshToken> findByUser(User user);
    void deleteByUser(User user);

    /**
     * Pessimistic write lock ke saath token fetch karo.
     *
     * SECURITY: Pehle check-then-revoke bina lock ke hota tha — do concurrent
     * refresh requests dono "not revoked" dekh ke pass ho jate the aur dono
     * valid token chains ban jati thi. Ab sirf ek hi rotate ho sakta hai.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.jti = :jti")
    Optional<RefreshToken> findByJtiForUpdate(@Param("jti") String jti);

    /**
     * Token family invalidate karo — kisi bhi refresh token ke reuse hone par
     * us user ke saare refresh tokens revoke kar deta hai (stolen token
     * detection).
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    int revokeAllForUser(@Param("userId") UUID userId);
}
