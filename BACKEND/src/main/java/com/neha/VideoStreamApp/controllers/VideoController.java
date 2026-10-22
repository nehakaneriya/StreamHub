package com.neha.VideoStreamApp.controllers;


import com.neha.VideoStreamApp.dtos.response.ScrollResponse;
import com.neha.VideoStreamApp.entities.User;
import com.neha.VideoStreamApp.entities.Video;
import com.neha.VideoStreamApp.dtos.response.VideoDto;
import com.neha.VideoStreamApp.dtos.response.UserChannelDto;
import com.neha.VideoStreamApp.exception.ResourceNotFoundException;
import com.neha.VideoStreamApp.playload.CustomMessage;
import com.neha.VideoStreamApp.repositories.UserRepository;
import com.neha.VideoStreamApp.services.AppConstants;
import com.neha.VideoStreamApp.services.VideoService;
import com.neha.VideoStreamApp.services.impl.VideoServiceImpl;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {

    // Max bytes jo ek range request me memory me load ho sakte hain (8 MB)
    private static final long MAX_RANGE_CHUNK_BYTES = 8L * 1024 * 1024;

    // Channel / my-videos listing ke liye max videos
    private static final int MAX_CHANNEL_VIDEOS = 500;

    // Guest view-count dedup ke liye visitor cookie ka naam
    private static final String VISITOR_COOKIE_NAME = "vs_visitor";

    private final VideoService videoService;
    private final UserRepository userRepository;
    private final com.neha.VideoStreamApp.repositories.CommentRepository commentRepository;


    //1. Upload Video API
    @PostMapping
    public ResponseEntity<?> create(
            @RequestParam("file")MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam(required = false) String category,
            Authentication authentication
            ){

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized access");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Logged in user not found in DB"));

                 // Create a Video object
                Video video=new Video();
                video.setTitle(title);
                video.setDescription(description);
                video.setUser(user);

                // Category (optional — backend 'other' default de dega agar null/blank hai)
                if (category != null && !category.isBlank()) {
                    video.setCategory(category.trim().toLowerCase());
                }

                // Save video metadata + file
                VideoDto savedVideo = videoService.save(video, file);

                if(savedVideo!=null)
                {
                    return ResponseEntity
                            .status(HttpStatus.CREATED)
                            .body(savedVideo);
                }
                else {
                    return ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(CustomMessage.builder()
                                    .message("Video Not Uploaded ")
                                    .success(false)
                                    .build()
                            );
                }
    }

    //2. Record a unique view (public — bina login bhi guest dekh sakta hai)
    //   Same user + same video lifetime me ek baar hi count hota hai
    @PostMapping("/{videoId}/view")
    public ResponseEntity<?> incrementView(
            @PathVariable String videoId,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "X-Real-IP", required = false) String realIp,
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        UUID userId = null;
        if (authentication != null && authentication.getName() != null) {
            // Login user — unique check user_id se hoga
            var userOpt = userRepository.findByEmailIgnoreCase(authentication.getName());
            if (userOpt.isPresent()) {
                userId = userOpt.get().getId();
            }
        }

        // Guest ke liye visitor identity
        String viewerId = null;
        if (userId == null) {
            viewerId = resolveGuestViewerId(request, response, forwardedFor, realIp);
        }

        videoService.incrementView(videoId, userId, viewerId);
        return ResponseEntity.ok(Map.of("viewCount", videoService.get(videoId).getViewCount()));
    }

    /**
     * Guest viewer ki identity banao.
     *
     * BUG FIX: Pehle X-Forwarded-For client-controlled header use hota tha
     * (guest apne views fake kar sakta tha), aur jab header absent hota tha to
     * literal "unknown" use hota tha — jisse SAARE anonymous viewers ek hi
     * bucket me gir jate the aur sirf pehla guest hi count hota tha.
     *
     * Ab:
     *   1. Ek signed-in-guest ke liye ek random visitor cookie (HttpOnly) use
     *      hota hai — per-visitor dedup sahi chalta hai.
     *   2. Cookie na hone par remote address use hota hai (X-Real-IP nginx
     *      $remote_addr se set karta hai, client spoof nahi kar sakta).
     *   3. Value ko hash karke store karte hain (raw IP DB me nahi jata).
     */
    private String resolveGuestViewerId(HttpServletRequest request, HttpServletResponse response,
                                        String forwardedFor, String realIp) {
        // 1. Visitor cookie
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (VISITOR_COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {
                    return hashVisitorId(cookie.getValue());
                }
            }
        }

        // 2. IP based fallback
        String ip = null;
        if (realIp != null && !realIp.isBlank()) {
            // nginx sets this from $remote_addr — client-controlled nahi hai
            ip = realIp.trim();
        } else if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For me client apna IP aage push kar sakta hai —
            // isliye LAST entry lete hain (jo trusted proxy ne add kiya hota hai)
            String[] parts = forwardedFor.split(",");
            ip = parts[parts.length - 1].trim();
        } else {
            ip = request.getRemoteAddr();
        }

        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            // Koi IP nahi mila — ek naya random visitor id bana ke cookie set karo
            String newId = UUID.randomUUID().toString();
            setVisitorCookie(response, newId);
            return hashVisitorId(newId);
        }

        // IP pe bhi visitor cookie set kar do taaki IP change hone par
        // double-count na ho (aur consistent identity rahe)
        String visitorId = UUID.randomUUID().toString();
        setVisitorCookie(response, visitorId);
        return hashVisitorId(visitorId);
    }

    private void setVisitorCookie(HttpServletResponse response, String value) {
        ResponseCookie cookie = ResponseCookie.from(VISITOR_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(365))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // Raw IP / visitor id ko hash karke store karo (privacy)
    private String hashVisitorId(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    //3. Get All Videos
    @GetMapping
    public ResponseEntity<ScrollResponse<VideoDto>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdBefore,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String scrollId,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ){
        ScrollResponse<VideoDto> response = videoService.getAll(

                search,
                userId,
                createdAfter,
                createdBefore,
                contentType,
                category,
                scrollId,
                pageSize,
                sortBy,
                Sort.Direction.fromString(sortDirection)

        );
        return ResponseEntity.ok(response);
    }

    //3. Get a Single Video
    @GetMapping("/{videoId}")
    public ResponseEntity<VideoDto> getSingleVideo(@PathVariable String videoId) {
        VideoDto video = videoService.get(videoId);
        return ResponseEntity.ok(video);
    }

    //4. Stream Full Video
    //http://localhost:2911/api/v1/videos/stream/
    @GetMapping("/stream/{videoId}")
    public ResponseEntity<Resource> stream(@PathVariable String videoId) {

        VideoDto video = videoService.get(videoId);

        String contentType = video.getContentType();
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        // SECURITY: content-type hamesha video/* hona chahiye. Pehle client ka
        // content-type store hota tha (text/html possible) — jo API origin pe
        // stored XSS ban jata tha. Ab DTO me hamesha video/* derive hota hai.
        if (!contentType.startsWith("video/")) {
            contentType = "application/octet-stream";
        }

        // Load a video file from a system
        FileSystemResource resource = new FileSystemResource(video.getFilePath());

        if (!resource.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType(contentType))
                // nosniff — browser content-type sniff karke HTML na bana sake
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    //5. Stream Video in Chunks (Range Support)
    @GetMapping("/stream/range/{videoId}")
    public ResponseEntity<Resource> streamVideoRange(
    @PathVariable String videoId,
            @RequestHeader(value = "Range",required = false)  String range
    ) {

        VideoDto video = videoService.get(videoId);
        Path path = Paths.get(video.getFilePath());

        Resource resource = new FileSystemResource(path);

        String contentType = video.getContentType();
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        long fileLength = path.toFile().length();

        // If no Range header → return full video
        if (range == null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        }

        // Extract range values
        long rangeStart;
        long rangeEnd;

        String[] ranges = range.replace("bytes=", "").split("-");
        if (ranges.length < 1 || ranges[0].isBlank()) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
        }
        try {
            rangeStart = Long.parseLong(ranges[0]);
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
        }

        if (ranges.length > 1 && !ranges[1].isBlank()) {
            try {
                rangeEnd = Long.parseLong(ranges[1]);
            } catch (NumberFormatException e) {
                rangeEnd = rangeStart + AppConstants.CHUNK_SIZE - 1;
            }
        } else {
            rangeEnd = rangeStart + AppConstants.CHUNK_SIZE - 1;
        }

        // SECURITY: rangeStart validate nahi tha — "bytes=999999999-" par
        // contentLength negative ho jata tha (NegativeArraySizeException) aur
        // "bytes=0-2147483647" par poora file heap me load ho jata tha (OOM).
        if (rangeStart < 0 || rangeStart >= fileLength) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
        }
        if (rangeEnd < rangeStart) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
        }
        if (rangeEnd >= fileLength) {
            rangeEnd = fileLength - 1;
        }

        long contentLength = rangeEnd - rangeStart + 1;
        // Max chunk size limit — ek request se poora file memory me na aaye
        if (contentLength > MAX_RANGE_CHUNK_BYTES) {
            rangeEnd = rangeStart + MAX_RANGE_CHUNK_BYTES - 1;
            contentLength = rangeEnd - rangeStart + 1;
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            // skip() poora skip nahi kar pata — loop se guarantee
            long remaining = rangeStart;
            while (remaining > 0) {
                long skipped = inputStream.skip(remaining);
                if (skipped <= 0) {
                    // skip nahi hua → byte-wise padho
                    if (inputStream.read() < 0) {
                        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
                    }
                    remaining--;
                } else {
                    remaining -= skipped;
                }
            }

            byte[] data = new byte[(int) contentLength];
            // read() ek call me poora buffer fill nahi karta — loop se padho
            int totalRead = 0;
            while (totalRead < data.length) {
                int read = inputStream.read(data, totalRead, data.length - totalRead);
                if (read < 0) {
                    break;
                }
                totalRead += read;
            }
            if (totalRead < data.length) {
                // File beech me khatam ho gayi — actual bytes bhejo
                data = java.util.Arrays.copyOf(data, totalRead);
                contentLength = totalRead;
            }

            // Set HTTP headers for Partial Content
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add("Content-Range", "bytes " + rangeStart + "-" + (rangeStart + contentLength - 1) + "/" + fileLength);
            httpHeaders.add("Cache-Control", "no-store,must-revalidate");
            httpHeaders.add("Pragma", "no-cache");
            httpHeaders.add("Expires", "0");
            httpHeaders.add("X-Content-Type-Options", "nosniff");

            httpHeaders.setContentLength(contentLength);
            return ResponseEntity
                    .status(HttpStatus.PARTIAL_CONTENT)
                    .headers(httpHeaders)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new ByteArrayResource(data));


        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    //6. Serve HLS Files
    // HLS base directory from application.properties
    @Value("${file.video.hls}")
    private  String HLS_DIR;

    // Thumbnail (pehla frame) — feed ke cards isse use karte hain.
    // Pehle har card apna HLS master playlist load karta tha, jisse ek page
    // par 12+ playlist + segment requests ho jate the.
    @GetMapping("/{videoId}/thumbnail.jpg")
    public ResponseEntity<Resource> serveThumbnail(@PathVariable String videoId) {

        Path path = safeHlsPath(videoId, VideoServiceImpl.THUMBNAIL_FILE_NAME);
        if (path == null || !Files.exists(path)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Resource resource = new FileSystemResource(path);

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(resource);
    }

    //Serve Master Playlist (master.m3u8) — sab resolution variants list karta hai
    @GetMapping("/{videoId}/master.m3u8")
    public ResponseEntity<Resource> serverMasterFile(@PathVariable String videoId){

        Path path = safeHlsPath(videoId, "master.m3u8");
        if (path == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (!Files.exists(path)){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Resource resource=new FileSystemResource(path);

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_TYPE,"application/vnd.apple.mpegurl")
                .body(resource);
    }

    //Serve Resolution-specific Playlist (e.g. /{videoId}/720p/playlist.m3u8)
    @GetMapping("/{videoId}/{resolution}/playlist.m3u8")
    public ResponseEntity<Resource> serveResolutionPlaylist(
            @PathVariable String videoId,
            @PathVariable String resolution) {

        Path path = safeHlsPath(videoId, resolution, "playlist.m3u8");
        if (path == null || !Files.exists(path)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Resource resource = new FileSystemResource(path);

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .body(resource);
    }

    //Serve HLS Segment (.ts files) — ab resolution-wise subfolder mein hain
    @GetMapping("/{videoId}/{resolution}/{segment}.ts")
    public ResponseEntity<Resource> serveSegments(
            @PathVariable String videoId,
            @PathVariable String resolution,
            @PathVariable String segment) {

        //create a path for segment
        Path path = safeHlsPath(videoId, resolution, segment + ".ts");
        if (path == null || !Files.exists(path)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Resource resource = new FileSystemResource(path);

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_TYPE, "video/mp2t")
                .body(resource);

    }

    /**
     * HLS path banao — SECURITY: path variables user-controlled hain, isliye
     * har component validate hota hai aur final path HLS root ke andar
     * confirm kiya jata hai. Pehle seedha Paths.get() se ".." daalne par
     * traversal ka surface tha.
     *
     * @return resolved path, ya null agar path invalid hai
     */
    private Path safeHlsPath(String... parts) {
        try {
            Path base = Paths.get(HLS_DIR).toAbsolutePath().normalize();
            Path resolved = base;
            for (String part : parts) {
                if (part == null || part.isBlank()) {
                    return null;
                }
                // Sirf safe characters — "..", "/" aur "\" block
                if (part.contains("..") || part.contains("/") || part.contains("\\")) {
                    return null;
                }
                resolved = resolved.resolve(part);
            }
            resolved = resolved.normalize();
            // Containment check — HLS root ke bahar jaane na do
            if (!resolved.startsWith(base)) {
                return null;
            }
            return resolved;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    //7. UPDATE VIDEO METADATA (title/description only)
    @PutMapping("/{videoId}")
    public ResponseEntity<?> updateVideo(
            @PathVariable String videoId,
            @RequestBody(required = false) Map<String, String> updates,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String email = authentication.getName();
        VideoDto video = videoService.get(videoId);

        // Owner ya Admin hi edit kar sakta hai (delete ke same rule)
        // Ownership check userId se karte hain (email DTO me expose nahi hota)
        boolean isOwner = isVideoOwner(video, authentication);
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));

        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You are not allowed to edit this video");
        }

        String title = updates != null ? updates.get("title") : null;
        String description = updates != null ? updates.get("description") : null;
        String category = updates != null ? updates.get("category") : null;

        VideoDto updatedVideo = videoService.update(
                videoId,
                title,
                description,
                category
        );
        return ResponseEntity.ok(updatedVideo);
    }

    /**
     * Kya authenticated user is video ka owner hai?
     * userId se compare karte hain (email DTO me public expose nahi hota).
     */
    private boolean isVideoOwner(VideoDto video, Authentication authentication) {
        if (video == null || video.getUserId() == null || authentication == null) {
            return false;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(user -> video.getUserId().equals(user.getId().toString()))
                .orElse(false);
    }

    //7. DELETE VIDEO (DB + FOLDER)
    @DeleteMapping("/{videoId}")
    public ResponseEntity<String> deleteVideo(
            @PathVariable String videoId,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized");
        }
        VideoDto video = videoService.get(videoId);
        String email = authentication.getName();

        // Check if the authenticated user is the owner of the video or has an admin role
        boolean isOwner = isVideoOwner(video, authentication);

        // Check if the user has an admin role
        boolean isAdmin = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(role -> role.equals("ROLE_ADMIN"));


        if (!isOwner && !isAdmin) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(
                            "You are not allowed to delete this video"
                    );
        }


        videoService.delete(videoId);
        return ResponseEntity.ok("Video deleted successfully");
    }

    @GetMapping("/my-videos")
    public ResponseEntity<?> getMyVideos(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String email = authentication.getName();

        // User ko fetch karenge uske videos ke saath
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // N+1 fix: comment counts ek hi grouped query se lo (per-video query nahi)
        Map<String, Long> commentCountMap = new HashMap<>();
        commentRepository.countGroupedByVideoId().forEach(row ->
                commentCountMap.put((String) row[0], (Long) row[1]));

        // Bounded — unbounded list memory exhaustion ka risk
        List<VideoDto> myVideos = user.getVideos().stream()
                .limit(MAX_CHANNEL_VIDEOS)
                .map(v -> {
                    VideoDto dto = new VideoDto();
                    dto.setVideoId(v.getVideoId());
                    dto.setTitle(v.getTitle());
                    dto.setDescription(v.getDescription());
                    dto.setContentType(v.getContentType());
                    dto.setCategory(v.getCategory());
                    dto.setFilePath(v.getFilePath());
                    dto.setViewCount(v.getViewCount());
                    dto.setCommentCount(commentCountMap.getOrDefault(v.getVideoId(), 0L));
                    dto.setCreatedAt(v.getCreatedAt());
                    dto.setUserId(user.getId() != null ? user.getId().toString() : null);
                    dto.setUserName(user.getName());
                    dto.setUserEmail(user.getEmail());
                    return dto;
                }).collect(Collectors.toList());

        return ResponseEntity.ok(myVideos);
    }

    // Public channel page — koi bhi (bina login) user ke videos dekh sakta hai.
    // Sirf READ. user_id binary UUID hai, isliye findById(UUID) se dhundhte hain.
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getChannel(@PathVariable String userId) {

        User user;
        try {
            user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("User not found");
        }

        List<VideoDto> videos = user.getVideos().stream()
                .limit(MAX_CHANNEL_VIDEOS)
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(v -> {
                    VideoDto dto = new VideoDto();
                    dto.setVideoId(v.getVideoId());
                    dto.setTitle(v.getTitle());
                    dto.setDescription(v.getDescription());
                    dto.setContentType(v.getContentType());
                    dto.setCategory(v.getCategory());
                    dto.setFilePath(v.getFilePath());
                    dto.setViewCount(v.getViewCount());
                    dto.setCreatedAt(v.getCreatedAt());
                    dto.setUserId(user.getId() != null ? user.getId().toString() : null);
                    dto.setUserName(user.getName());
                    return dto;
                }).collect(Collectors.toList());

        UserChannelDto channel = UserChannelDto.builder()
                .userId(user.getId() != null ? user.getId().toString() : null)
                .userName(user.getName())
                .videoCount(videos.size())
                .videos(videos)
                .build();

        return ResponseEntity.ok(channel);
    }
}
