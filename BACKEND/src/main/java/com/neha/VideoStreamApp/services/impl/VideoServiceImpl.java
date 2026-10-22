package com.neha.VideoStreamApp.services.impl;

import com.neha.VideoStreamApp.cache.VideoCacheService;
import com.neha.VideoStreamApp.dtos.response.ScrollResponse;
import com.neha.VideoStreamApp.dtos.response.VideoDto;
import com.neha.VideoStreamApp.entities.User;
import com.neha.VideoStreamApp.entities.Video;
import com.neha.VideoStreamApp.entities.VideoView;
import com.neha.VideoStreamApp.exception.BadRequestException;
import com.neha.VideoStreamApp.exception.ResourceNotFoundException;
import com.neha.VideoStreamApp.exception.VideoProcessingException;
import com.neha.VideoStreamApp.helper.ScrollPositionCodec;
import com.neha.VideoStreamApp.repositories.CommentRepository;
import com.neha.VideoStreamApp.repositories.UserRepository;
import com.neha.VideoStreamApp.repositories.VideoRepository;
import com.neha.VideoStreamApp.repositories.VideoViewRepository;
import com.neha.VideoStreamApp.services.VideoService;
import com.neha.VideoStreamApp.specifications.VideoSpecification;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.neha.VideoStreamApp.dtos.response.VideoDto;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoServiceImpl implements VideoService {

    @Value("${files.video}")
    private String DIR;

    @Value("${file.video.hls}")
    private String HLS_DIR;

    @Value("${app.upload.max-size-bytes:104857600}")
    private long maxUploadSizeBytes;

    @Value("${app.upload.allowed-extensions:mp4,mov,avi,mkv,webm,m4v,flv,wmv,mpeg,mpg,3gp,ogv}")
    private String allowedExtensionsRaw;

    @Value("${app.upload.target-resolutions:1080p,720p,480p,240p}")
    private String targetResolutionsRaw;

    private Set<String> allowedExtensions;

    private final VideoRepository videoRepository;
    private final CommentRepository commentRepository;
    private final VideoViewRepository videoViewRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final VideoCacheService videoCacheService;
    private final com.neha.VideoStreamApp.repositories.CategoryRepository categoryRepository;

    // ================= ABR TARGET RESOLUTIONS =================
    // label, height, video-bitrate, maxrate, bufsize, audio-bitrate
    private record Resolution(String label, int height, String videoBitrate, String maxrate, String bufsize, String audioBitrate) {}

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;
    private static final long PROCESS_TIMEOUT_MINUTES = 10;
    private static final int MAX_USER_VIDEO_LIST_SIZE = 500;

    // Thumbnail ka file name (HLS output folder me)
    public static final String THUMBNAIL_FILE_NAME = "thumbnail.jpg";

    /**
     * Transcode karne wale resolutions.
     * CONFIGURABLE — chhote server (1-2 GB RAM) pe 4 resolutions transcode karne
     * me CPU/RAM bohot lagta hai. `app.upload.target-resolutions` se kam kar sakte ho
     * (jaise "720p,480p,240p" ya sirf "480p").
     */
    private static final List<Resolution> ALL_RESOLUTIONS = List.of(
            new Resolution("1080p", 1080, "5000k", "5350k", "7500k", "192k"),
            new Resolution("720p", 720, "2800k", "2996k", "4200k", "128k"),
            new Resolution("480p", 480, "1400k", "1498k", "2100k", "128k"),
            new Resolution("240p", 240, "600k", "642k", "900k", "96k")
    );

    private List<Resolution> targetResolutions;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(DIR));
            Files.createDirectories(Paths.get(HLS_DIR));
        } catch (IOException e) {
            throw new VideoProcessingException("Could not create video directories", e);
        }
        allowedExtensions = Arrays.stream(allowedExtensionsRaw.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        // Transcode resolutions — config se filter karo (chhote server ke liye)
        List<String> requested = Arrays.stream(targetResolutionsRaw.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase())
                .filter(s -> !s.isEmpty())
                .toList();

        if (requested.isEmpty()) {
            targetResolutions = ALL_RESOLUTIONS;
        } else {
            targetResolutions = ALL_RESOLUTIONS.stream()
                    .filter(r -> requested.contains(r.label()))
                    .toList();
            if (targetResolutions.isEmpty()) {
                log.warn("[UPLOAD] app.upload.target-resolutions me koi valid resolution nahi mila — sabhi default use honge");
                targetResolutions = ALL_RESOLUTIONS;
            } else {
                log.info("[UPLOAD] Transcode resolutions: {}", targetResolutions.stream()
                        .map(Resolution::label).toList());
            }
        }
    }

    @Override
    public VideoDto save(Video video, MultipartFile file) {

        // 1. Empty file check
        if (file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }

        // 2. Size check — config se limit (default 100 MB)
        if (file.getSize() > maxUploadSizeBytes) {
            throw new BadRequestException("File is too large. Maximum allowed size is "
                    + (maxUploadSizeBytes / (1024 * 1024)) + " MB");
        }

        // 3. Filename sanitize — sirf safe characters rakho
        String raw = file.getOriginalFilename();
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Invalid file name");
        }
        String originalFilename = sanitizeFileName(raw);

        // 4. Extension allow-list check
        String extension = extensionOf(originalFilename);
        if (!allowedExtensions.contains(extension)) {
            throw new BadRequestException("Unsupported file type. Allowed extensions: "
                    + String.join(", ", allowedExtensions));
        }

        // 5. Generate unique filename (overwrite problem solved)
        String uniqueFileName = UUID.randomUUID() + "_" + originalFilename;

        // 6. Final path where video will be saved + containment check
        Path baseDir = Paths.get(DIR).toAbsolutePath().normalize();
        Path filePath = baseDir.resolve(uniqueFileName).normalize();
        if (!filePath.startsWith(baseDir)) {
            throw new BadRequestException("Invalid file path");
        }

        Video savedVideo = null;
        Path hlsDir = Paths.get(HLS_DIR).toAbsolutePath().normalize().resolve(video.getVideoId() != null ? video.getVideoId() : "tmp");

        try {
            // 7. Save file to disk (try-with-resources → no memory leak)
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            // 8. Save metadata in a database
            video.setFilePath(filePath.toString());
            // Content-Type client pe se trust NAHI karte — extension se derive karo.
            // Warna text/html upload karke API origin pe stored XSS serve ho sakta tha.
            video.setContentType(contentTypeFor(extension));

            // Category nahi bheji gayi to default 'other' set karo
            if (video.getCategory() == null || video.getCategory().isBlank()) {
                video.setCategory(CategoryServiceImpl.DEFAULT_CATEGORY_SLUG);
            } else {
                video.setCategory(validateCategory(video.getCategory()));
            }

            savedVideo = videoRepository.save(video);

            // 9. Start video processing (HLS conversion)
            processVideo(savedVideo.getVideoId());

            // 10. Naya video add hua — cached video list purani ho gayi, clear karo
            evictScrollCacheAfterCommit();

            // 11. Return saved video info
            return mapToDto(savedVideo);

        } catch (IOException e) {
            cleanupFailedUpload(filePath, savedVideo);
            throw new VideoProcessingException("Video upload failed", e);
        } catch (RuntimeException e) {
            // DB save ya transcode fail hua → disk pe reh gayi file aur DB row ko clean karo
            cleanupFailedUpload(filePath, savedVideo);
            throw e;
        }
    }

    // Transcode/DB fail hone par disk + DB se leftovers clean karo
    private void cleanupFailedUpload(Path filePath, Video savedVideo) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            log.warn("Could not delete failed upload file: {}", filePath);
        }
        if (savedVideo != null && savedVideo.getVideoId() != null) {
            try {
                deleteHlsDirectory(savedVideo.getVideoId());
            } catch (Exception ignored) {
                log.warn("Could not delete HLS directory for failed upload: {}", savedVideo.getVideoId());
            }
            try {
                videoRepository.delete(savedVideo);
            } catch (Exception ignored) {
                log.warn("Could not delete DB row for failed upload: {}", savedVideo.getVideoId());
            }
        }
    }

    // Filename se har unsafe character hata do — sirf letters, digits, dot, dash, underscore
    private String sanitizeFileName(String raw) {
        String cleaned = StringUtils.cleanPath(raw);
        // basename lo — directory components hata do
        int lastSlash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            cleaned = cleaned.substring(lastSlash + 1);
        }
        // Sirf safe characters rakho
        cleaned = cleaned.replaceAll("[^A-Za-z0-9._-]", "_");
        // ".." jaisa koi bhi sequence hata do
        while (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", ".");
        }
        cleaned = cleaned.replaceAll("^\\.+", "");
        if (cleaned.isBlank()) {
            throw new BadRequestException("Invalid file name");
        }
        // Filename length limit (filesystem limit + header injection se bachao)
        if (cleaned.length() > 120) {
            String ext = extensionOf(cleaned);
            cleaned = cleaned.substring(0, 120 - (ext.isEmpty() ? 0 : ext.length() + 1));
            if (!ext.isEmpty()) {
                cleaned = cleaned + "." + ext;
            }
        }
        return cleaned;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    // Extension se content-type derive karo — client ka content-type kabhi trust nahi karte
    private String contentTypeFor(String extension) {
        return switch (extension) {
            case "mp4", "m4v" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "avi" -> "video/x-msvideo";
            case "mkv" -> "video/x-matroska";
            case "webm" -> "video/webm";
            case "flv" -> "video/x-flv";
            case "wmv" -> "video/x-ms-wmv";
            case "mpeg", "mpg" -> "video/mpeg";
            case "3gp" -> "video/3gpp";
            case "ogv" -> "video/ogg";
            default -> "video/mp4";
        };
    }


    @Override
    public VideoDto get(String videoId) {

        Video video = videoRepository.findById(videoId).orElseThrow(() -> new ResourceNotFoundException("Video not found"));
        return mapToDto(video);
    }

    @Override
    public VideoDto getByTitle(String title) {
        // Pehle ye UnsupportedOperationException throw karta tha (500 + dead code)
        return videoRepository.findByTitle(title)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found"));
    }

    @Override
    public ScrollResponse<VideoDto> getAll(String search,
                                           UUID userId,
                                           Instant createdAfter,
                                           Instant createdBefore,
                                           String contentType,
                                           String category,

                                           String scrollId,
                                           int pageSize,
                                           String sortBy,
                                           Sort.Direction sortDirection) {

        log.debug("Checking cache for search={} userId={} createdAfter={} createdBefore={} contentType={} category={} scrollId={} pageSize={} sortBy={} sortDirection={}",search,userId,createdAfter,createdBefore,contentType,category,scrollId,pageSize,sortBy,sortDirection);

        // Page size ko clamp karo — client pageSize=1000000 bhej kar poori table
        // heap me load kar sakta tha (OOM DoS).
        int effectivePageSize = clampPageSize(pageSize);

        ScrollResponse<VideoDto> cachedScrollResponse = videoCacheService.getCachedScrollResponse(search, userId, createdAfter, createdBefore, contentType, category, scrollId, effectivePageSize, sortBy, sortDirection);

        if (cachedScrollResponse != null) {
            log.debug("Cache hit for search={} userId={} createdAfter={} createdBefore={} contentType={} category={} scrollId={} pageSize={} sortBy={} sortDirection={}",search,userId,createdAfter,createdBefore,contentType,category,scrollId,pageSize,sortBy,sortDirection);
            return cachedScrollResponse;
        }

        log.debug("Cache miss,querying database");

        ScrollPosition scrollPosition;
        try {
            scrollPosition = ScrollPositionCodec.decode(scrollId);
        } catch (IllegalArgumentException e) {
            // Corrupt/expired scrollId — 400 return karo. Pehle yahan silently
            // page 1 return hota tha jisse client "load more" loop me atak jata tha.
            throw new BadRequestException("Invalid scrollId. Please restart pagination.");
        }

        Specification<Video> specification = VideoSpecification.build(
                search,
                userId,
                createdAfter,
                createdBefore,
                contentType,
                category
        );

        Sort.Direction direction=(sortDirection == null || sortDirection == Sort.Direction.ASC)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        String validatedSortBy = validateAndMapSortField(sortBy);

        Sort sort;
        if (validatedSortBy.equals("createdAt")) {
            sort = Sort.by(direction, validatedSortBy);
        } else {
            // For non-unique fields, add a secondary sort by createdAt to ensure consistent ordering
            sort = Sort.by(direction, validatedSortBy).and(Sort.by(direction,"createdAt"));
        }

        // Statement is for actual database call
        Window<Video> window = videoRepository.findBy(
                specification,
                query -> query.limit(effectivePageSize)
                        .sortBy(sort)
                        .scroll(scrollPosition));

        // N+1 fix: Video.user LAZY hai — har row ke liye alag SELECT jaata tha.
        // Ab users ko ek query me batch load karte hain.
        List<Video> videoEntities = window.getContent();
        Map<UUID, User> userMap = loadUsers(videoEntities);

        List<VideoDto> videos = videoEntities
                .stream()
                .map(v -> mapToDto(v, userMap))
                .toList();

        String nextScrollId = null;
        if (!window.isEmpty() && window.hasNext()) {
            ScrollPosition nextPosition = window.positionAt(window.size() - 1);
            nextScrollId = ScrollPositionCodec.encode(nextPosition);
        }


        ScrollResponse<VideoDto> responseToReturn = ScrollResponse.<VideoDto>builder()
                .content(videos)
                .scrollId(nextScrollId)
                .hasNext(window.hasNext())
                .pageSize(effectivePageSize)
                .build();
        log.debug("Caching ScrollResponse for Future requests");
        videoCacheService.cacheScrollResponse(responseToReturn, search, userId, createdAfter, createdBefore, contentType, category, scrollId, effectivePageSize, sortBy, sortDirection);
        return responseToReturn;
    }

    // Page size ko safe range me clamp karo (1..50)
    private int clampPageSize(int pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * Category slug validate karo.
     *
     * Pehle koi bhi client string DB me likh di jati thi — jo categories table
     * me exist nahi karti thi (referential integrity drift) aur 50+ chars hone
     * par MySQL truncation error (500) deti thi.
     */
    private String validateCategory(String category) {
        String slug = category.trim().toLowerCase();

        if (slug.length() > 50) {
            throw new BadRequestException("Category name is too long (max 50 characters)");
        }
        if (!slug.matches("^[a-z0-9-]+$")) {
            throw new BadRequestException("Invalid category");
        }
        if (CategoryServiceImpl.DEFAULT_CATEGORY_SLUG.equals(slug)) {
            return slug;
        }
        if (!categoryRepository.existsBySlug(slug)) {
            throw new BadRequestException("Unknown category: " + slug);
        }
        return slug;
    }

    // Validate and map sort field to prevent SQL injection and ensure valid fields
    // Validate and map sort field
    private String validateAndMapSortField(String sortBy) {

        if (sortBy == null || sortBy.isBlank()) {
            return "createdAt";
        }
        return switch (sortBy.toLowerCase()) {

            case "videoid", "id" -> "videoId";
            case "title", "name" -> "title";
            case "description", "desc" -> "description";
            case "contenttype", "type", "format" -> "contentType";
            case "filepath", "path" -> "filePath";
            case "created", "createdat", "createat" -> "createdAt";
            case "updated", "updatedat", "updateat" -> "updatedAt";
            default -> "createdAt";
        };
    }

    // ================= ABR VIDEO PROCESSING (multi-resolution) =================
    @Override
    public String processVideo(String videoId) {
        Video video = videoRepository.findById(videoId).orElseThrow(() -> new ResourceNotFoundException("Video not found"));

        // Containment check — file path aur HLS output dono storage root ke andar hone chahiye
        Path baseVideoDir = Paths.get(DIR).toAbsolutePath().normalize();
        Path hlsBaseDir = Paths.get(HLS_DIR).toAbsolutePath().normalize();
        Path videoPath = Paths.get(video.getFilePath()).toAbsolutePath().normalize();
        if (!videoPath.startsWith(baseVideoDir)) {
            throw new VideoProcessingException("Video file is outside the storage directory");
        }
        Path outputBase = hlsBaseDir.resolve(videoId).normalize();
        if (!outputBase.startsWith(hlsBaseDir)) {
            throw new VideoProcessingException("Invalid HLS output directory");
        }

        try {
            Files.createDirectories(outputBase);

            // 1. Original video ki width/height ffprobe se nikalo
            int[] dimensions = getVideoDimensions(videoPath);
            int originalWidth = dimensions[0];
            int originalHeight = dimensions[1];
            double aspectRatio = (double) originalWidth / originalHeight;

            // 2. Sirf wahi resolutions banao jo original se chhoti/equal hain
            //    (upscaling se quality nahi sudhregi, sirf storage/time waste hoga)
            List<Resolution> eligible = targetResolutions.stream()
                    .filter(r -> r.height() <= originalHeight)
                    .toList();

            // Agar original video sabse chhoti target resolution se bhi chhota hai,
            // to kam se kam ek variant zaroor banao
            if (eligible.isEmpty()) {
                eligible = List.of(targetResolutions.get(targetResolutions.size() - 1));
            }

            // 3. Har eligible resolution ke liye alag ffmpeg command chalao (sequential loop)
            List<Resolution> generated = new ArrayList<>();
            for (Resolution res : eligible) {
                boolean success = transcodeToResolution(videoPath, outputBase, res);
                if (success) {
                    generated.add(res);
                }
            }

            if (generated.isEmpty()) {
                throw new VideoProcessingException("Video Processing Fail !! No resolution could be generated");
            }

            // 4. Master playlist generate karo — jo sab available variants ko list kare
            generateMasterPlaylist(outputBase, generated, aspectRatio);

            // 5. Thumbnail banao (pehla frame) — feed ke cards isse use karte hain.
            //    Pehle har card apna HLS master playlist load karta tha (12+ playlist
            //    + segment requests per page).
            generateThumbnail(videoPath, outputBase);

            return videoId;

        } catch (IOException ex) {
            throw new VideoProcessingException("Video Processing fail !!", ex);
        }
    }

    // ffprobe se original video ka width aur height nikalta hai
    private int[] getVideoDimensions(Path videoPath) {
        try {
            List<String> cmd = List.of(
                    "ffprobe", "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=width,height",
                    "-of", "csv=p=0",
                    videoPath.toString()
            );

            ProcessBuilder processBuilder = buildProcess(cmd);
            Process process = processBuilder.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.readLine();
            }

            // Timeout lagao — process hang ho to request thread block na rahe
            boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new VideoProcessingException("ffprobe timed out");
            }
            int exit = process.exitValue();
            if (exit != 0 || output == null || output.isBlank()) {
                throw new VideoProcessingException("Could not detect video resolution (ffprobe failed)");
            }

            // csv output format: width,height
            // Parsing defensive honi chahiye — pehle parts[0]/parts[1] se
            // ArrayIndexOutOfBoundsException aa sakta tha (500 + raw message leak)
            String[] parts = output.trim().split(",");
            if (parts.length < 2) {
                throw new VideoProcessingException("Could not parse video dimensions (unexpected ffprobe output)");
            }
            int width;
            int height;
            try {
                width = Integer.parseInt(parts[0].trim());
                height = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new VideoProcessingException("Could not parse video dimensions");
            }
            if (width <= 0 || height <= 0) {
                throw new VideoProcessingException("Could not detect video resolution (invalid dimensions)");
            }
            return new int[]{width, height};

        } catch (IOException e) {
            throw new VideoProcessingException("ffprobe execution failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VideoProcessingException("ffprobe interrupted", e);
        }
    }

    // Ek resolution ke liye ffmpeg se HLS segments + playlist banata hai
    private boolean transcodeToResolution(Path inputPath, Path outputBase, Resolution res) {
        try {
            Path resDir = outputBase.resolve(res.label());
            Files.createDirectories(resDir);

            List<String> ffmpegCmd = List.of(
                    "ffmpeg", "-y", "-i", inputPath.toString(),
                    "-vf", "scale=-2:" + res.height(),
                    "-c:v", "libx264", "-preset", "veryfast",
                    "-b:v", res.videoBitrate(), "-maxrate", res.maxrate(), "-bufsize", res.bufsize(),
                    "-c:a", "aac", "-b:a", res.audioBitrate(),
                    "-hls_time", "10", "-hls_list_size", "0",
                    "-hls_segment_filename", resDir + "/segment_%3d.ts",
                    resDir + "/playlist.m3u8"
            );

            ProcessBuilder processBuilder = buildProcess(ffmpegCmd);
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            // Timeout — runaway ffmpeg request threads na rok kar le
            boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.warn("ffmpeg timed out for resolution {}", res.label());
                return false;
            }
            return process.exitValue() == 0;

        } catch (IOException e) {
            throw new VideoProcessingException("Video Processing fail for resolution: " + res.label(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VideoProcessingException("Video processing interrupted for resolution: " + res.label(), e);
        }
    }

    // Thumbnail generate karo — video ka pehla frame (2 second par)
    private void generateThumbnail(Path inputPath, Path outputBase) {
        Path thumbnail = outputBase.resolve(THUMBNAIL_FILE_NAME);
        try {
            List<String> cmd = List.of(
                    "ffmpeg", "-y",
                    "-ss", "2",
                    "-i", inputPath.toString(),
                    "-frames:v", "1",
                    "-q:v", "3",
                    thumbnail.toString()
            );

            ProcessBuilder processBuilder = buildProcess(cmd);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            // Output drain karo warna process buffer fill hone se atak jayega
            try (InputStream ignored = process.getInputStream()) {
                // no-op
            }
            boolean finished = process.waitFor(2, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Thumbnail generation timed out");
                return;
            }
            if (process.exitValue() != 0 || !Files.exists(thumbnail)) {
                log.warn("Thumbnail generation failed for {}", inputPath.getFileName());
            }
        } catch (IOException e) {
            // Thumbnail failure se video upload fail nahi hona chahiye
            log.warn("Could not generate thumbnail: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Thumbnail generation interrupted");
        }
    }

    // master.m3u8 likhta hai — sab generated resolutions ko bandwidth+resolution tag ke saath list karta hai
    private void generateMasterPlaylist(Path outputBase, List<Resolution> generated, double aspectRatio) throws IOException {        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");

        for (Resolution res : generated) {
            int bandwidth = parseBitrateToBps(res.videoBitrate()) + parseBitrateToBps(res.audioBitrate());
            int width = calculateWidth(res.height(), aspectRatio);
            sb.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(bandwidth)
                    .append(",RESOLUTION=").append(width).append("x").append(res.height()).append("\n");
            sb.append(res.label()).append("/playlist.m3u8\n");
        }

        Files.writeString(outputBase.resolve("master.m3u8"), sb.toString());
    }

    // "2800k" -> 2800000
    private int parseBitrateToBps(String bitrate) {
        String cleaned = bitrate.toLowerCase().replace("k", "");
        return Integer.parseInt(cleaned) * 1000;
    }

    // Height se width nikalta hai (original aspect ratio maintain karke), even number banata hai
    private int calculateWidth(int height, double aspectRatio) {
        int width = (int) Math.round(height * aspectRatio);
        if (width % 2 != 0) width += 1;
        return width;
    }

    // ProcessBuilder banata hai — args ko LIST ke roop me pass karo, kabhi string join karke
    // cmd.exe /c me na daalo. cmd.exe apna command parsing khud karta hai, jisse
    // user-controlled filename (& | > ^ %) se command injection ho sakta tha.
    private ProcessBuilder buildProcess(List<String> cmd) {
        return new ProcessBuilder(cmd);
    }

    @Override
    @Transactional
    public VideoDto update(String videoId, String title, String description, String category) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found"));

        // Title/description sirf tab update karo jab naye values bheji gayi hon
        if (title != null && !title.isBlank()) {
            video.setTitle(title);
        }
        if (description != null) {
            video.setDescription(description);
        }

        // Category update
        if (category != null && !category.isBlank()) {
            video.setCategory(validateCategory(category));
        }

        Video updatedVideo = videoRepository.save(video);

        // Video edit hua — cached feed purani ho gayi, clear karo
        evictScrollCacheAfterCommit();

        return mapToDto(updatedVideo);
    }

    @Override
    @Transactional
    public void delete(String videoId) {

        //DB se video lao
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found"));

        commentRepository.deleteByVideo_VideoIdAndParentCommentIsNotNull(videoId);
        commentRepository.deleteByVideo_VideoId(videoId);
        videoViewRepository.deleteByVideo_VideoId(videoId);

        // Path capture kar lo — file delete transaction ke BAAD hoga
        Path originalFile = Paths.get(video.getFilePath());

        //DB record delete
        videoRepository.delete(video);

        // ── File delete TRANSACTION ke bahar ──────────────────────────────
        // Pehle file delete hota tha aur phir DB delete — file delete fail hone
        // par transaction rollback hota tha lekin DB row bacha rehta tha
        // (aur vice-versa: commit fail ho to files permanently gayab).
        // Ab: DB delete + file delete alag-alag, dono best-effort.
        deleteFileQuietly(originalFile);
        deleteHlsDirectory(videoId);

        // Video delete hua — cached list clear karo
        evictScrollCacheAfterCommit();
    }

    // File delete best-effort — failure pe exception nahi, sirf log
    private void deleteFileQuietly(Path path) {
        try {
            if (path != null && Files.exists(path)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("Could not delete video file {}: {}", path, e.getMessage());
        }
    }

    // HLS folder delete — best effort, exception message me absolute path leak na ho
    private void deleteHlsDirectory(String videoId) {
        try {
            Path base = Paths.get(HLS_DIR).toAbsolutePath().normalize();
            Path dir = base.resolve(videoId).normalize();
            // containment check — HLS dir ke bahar jaane na do
            if (!dir.startsWith(base)) {
                log.warn("Refusing to delete HLS directory outside storage root: {}", videoId);
                return;
            }
            if (Files.exists(dir)) {
                deleteDirectory(dir.toFile());
            }
        } catch (Exception e) {
            log.warn("Could not delete HLS directory for video {}: {}", videoId, e.getMessage());
        }
    }

    // Cache eviction transaction COMMIT ke baad kare — warna ek concurrent read
    // pre-commit data se cache repopulate kar deta tha (10 min stale feed).
    private void evictScrollCacheAfterCommit() {
        videoCacheService.evictScrollCacheAfterCommit();
    }


    // View record karta hai — 30s watch ke baad frontend bhejta hai.
    // Same user + same video ka view 24h window me ek baar hi count hota hai (refresh spam protection)
    @Override
    @Transactional
    public void incrementView(String videoId, UUID userId, String viewerIp) {

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found"));

        // 24h window — last 24 ghante me view ho chuka hai kya?
        Instant windowStart = Instant.now().minus(Duration.ofHours(24));

        boolean alreadyCounted;

        if (userId != null) {
            alreadyCounted = videoViewRepository
                    .existsByVideo_VideoIdAndUser_IdAndViewedAtAfter(videoId, userId, windowStart);
        } else {
            // Guest — IP based check
            alreadyCounted = viewerIp != null && videoViewRepository
                    .existsByVideo_VideoIdAndViewerIpAndViewedAtAfter(videoId, viewerIp, windowStart);
        }

        if (alreadyCounted) {
            return; // 24h me ek hi view — count nahi badhega
        }

        // Naya view — video_views me row insert karo aur video ka total count +1 karo
        User viewer = null;
        if (userId != null) {
            viewer = userRepository.findById(userId).orElse(null);
        }

        videoViewRepository.save(VideoView.builder()
                .video(video)
                .user(viewer)
                .viewerIp(userId != null ? null : viewerIp)
                .viewedAt(Instant.now())
                .build());

        // Atomic increment — read-modify-write se lost updates nahi honge
        videoRepository.incrementViewCount(videoId);

        // Count badal gaya — cached list clear karo
        evictScrollCacheAfterCommit();
    }


    @Override
    @Transactional(readOnly = true)
    public List<VideoDto> getVideosByUserEmail(String email) {
        // Bounded — unbounded list memory exhaustion ka risk
        List<Video> videos = videoRepository.findByUserEmail(email).stream()
                .limit(MAX_USER_VIDEO_LIST_SIZE)
                .toList();
        return videos.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    // Absolute path exception message me leak na ho
                    if (!file.delete()) {
                        log.warn("Could not delete file during directory cleanup: {}", file.getName());
                    }
                }
            }
        }
        if (!dir.delete()) {
            log.warn("Could not delete directory during cleanup: {}", dir.getName());
        }
    }

    private VideoDto mapToDto(Video video) {
        return mapToDto(video, null);
    }

    /**
     * Video -> DTO mapping.
     *
     * @param userMap pre-loaded users (N+1 avoid karne ke liye). Null hone par
     *                user lazily load ho jayega.
     */
    private VideoDto mapToDto(Video video, Map<UUID, User> userMap) {
        VideoDto dto = new VideoDto();
        dto.setVideoId(video.getVideoId());
        dto.setTitle(video.getTitle());
        dto.setDescription(video.getDescription());
        dto.setContentType(video.getContentType());
        dto.setCategory(video.getCategory());
        dto.setFilePath(video.getFilePath());
        dto.setViewCount(video.getViewCount());
        dto.setCreatedAt(video.getCreatedAt());
        if (video.getUser() != null) {
            dto.setUserId(video.getUser().getId() != null ? video.getUser().getId().toString() : null);
            dto.setUserName(video.getUser().getName());
            // SECURITY: userEmail yahan populate NAHI hota — ye mapping public
            // feed / single video / channel ke liye hai. Email expose karne se
            // koi bhi anonymous visitor uploader ke email harvest kar sakta tha.
            // Admin listing alag mapping (AdminServiceImpl) use karti hai.
        }
        return dto;
    }

    // N+1 fix: in videos ke saare users ek hi query me load karo
    private Map<UUID, User> loadUsers(List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = videos.stream()
                .map(Video::getUser)
                .filter(Objects::nonNull)
                // getId() lazy proxy pe initialization trigger nahi karta
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, User> map = new HashMap<>();
        for (User user : userRepository.findAllById(ids)) {
            map.put(user.getId(), user);
        }
        return map;
    }

}
