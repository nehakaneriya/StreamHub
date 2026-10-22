package com.neha.StreamHub.repositories;

import com.neha.StreamHub.entities.VideoView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface VideoViewRepository extends JpaRepository<VideoView, Long> {

    // Kya is user ne is video ka view last 24 ghante (window) me kiya hai?
    boolean existsByVideo_VideoIdAndUser_IdAndViewedAtAfter(String videoId, UUID userId, Instant after);

    // Guest ke liye — IP based 24h check
    boolean existsByVideo_VideoIdAndViewerIpAndViewedAtAfter(String videoId, String viewerIp, Instant after);

    // Admin: saare videos ke unique views — [videoId, count] pairs
    @Query("SELECT vv.video.videoId, COUNT(vv) FROM VideoView vv GROUP BY vv.video.videoId")
    List<Object[]> countGroupedByVideoId();

    // Video delete hone par uske saare view records cleanup
    void deleteByVideo_VideoId(String videoId);
}
