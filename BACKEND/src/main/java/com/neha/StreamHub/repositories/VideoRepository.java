package com.neha.StreamHub.repositories;

import com.neha.StreamHub.entities.Video;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video,String>, JpaSpecificationExecutor<Video>
{
    Optional<Video> findByTitle (String title);

    List<Video> findByUserEmail(String email);

    long countByCategory(String category);

    // Category delete hone par uske videos ko 'other' me move karne ke liye
    @Modifying
    @Query("UPDATE Video v SET v.category = :toSlug WHERE v.category = :fromSlug")
    int moveCategory(@Param("fromSlug") String fromSlug, @Param("toSlug") String toSlug);

    // Admin: har category me kitne videos hain — [category, count] pairs
    @Query("SELECT v.category, COUNT(v) FROM Video v GROUP BY v.category")
    List<Object[]> countGroupedByCategory();

    // Atomic view count increment — DB level me.
    // Pehle read-modify-write hota tha (setViewCount(get+1)) jo concurrent
    // requests me updates lose karta tha.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Video v SET v.viewCount = v.viewCount + 1 WHERE v.videoId = :videoId")
    int incrementViewCount(@Param("videoId") String videoId);
}
