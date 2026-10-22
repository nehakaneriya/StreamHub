package com.neha.StreamHub.repositories;

import com.neha.StreamHub.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    // Naya method: Har user ka email aur unke total videos ka count nikalne ke liye
    @Query("SELECT u.email, COUNT(v) FROM User u LEFT JOIN u.videos v GROUP BY u.email")
    List<Object[]> countVideosPerUser();

    // Counts — poori table load karne ke bajaye DB side count (admin dashboard)
    long countByEnableTrue();

    long countByRoles_Name(String roleName);

    // Email lookup case-insensitive — login ke liye
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);
}
