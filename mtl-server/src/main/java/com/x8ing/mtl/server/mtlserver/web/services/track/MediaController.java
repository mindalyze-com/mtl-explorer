package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaFile;
import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaPointDTO;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.ManualMediaLocationRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaDetailsDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendItemPageDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendItemsRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendResponseDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTimeCorrectionRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaPageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/media")
@JsonPropertyOrder({
        "mediaRepository",
        "trackMediaService",
        "mediaPositionService",
        "mediaTrendService",
        "videoThumbnailService",
        "mediaProcessLimiter"
})
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private static final int MEDIA_CONTENT_CACHE_HOURS = 1;
    private static final long EMPTY_MEDIA_FILE_SIZE_BYTES = 0L;
    private static final String IMAGE_MAGICK_COMMAND = "convert";
    private static final String IMAGE_MAGICK_QUALITY = "92";

    private final MediaRepository mediaRepository;
    private final TrackMediaService trackMediaService;
    private final MediaPositionService mediaPositionService;
    private final MediaTrendService mediaTrendService;
    private final VideoThumbnailService videoThumbnailService;
    private final MediaProcessLimiter mediaProcessLimiter;

    public MediaController(
            MediaRepository mediaRepository,
            TrackMediaService trackMediaService,
            MediaPositionService mediaPositionService,
            MediaTrendService mediaTrendService,
            VideoThumbnailService videoThumbnailService,
            MediaProcessLimiter mediaProcessLimiter) {
        this.mediaRepository = mediaRepository;
        this.trackMediaService = trackMediaService;
        this.mediaPositionService = mediaPositionService;
        this.mediaTrendService = mediaTrendService;
        this.videoThumbnailService = videoThumbnailService;
        this.mediaProcessLimiter = mediaProcessLimiter;
    }

    @RequestMapping("/get-media-with-location-info")
    public List<MediaFile> getMediaWithLocationInfo(
            @RequestParam(name = "light", required = false, defaultValue = "true") boolean light) {
        List<MediaFile> media = mediaRepository.findMediaWithLocationInfo();
        if (light && media != null) {
            media.forEach(mediaFile -> {
                // remove info which won't be required
                mediaFile.setIndexedFile(null);
                mediaFile.setCameraModel(null);
                mediaFile.setCameraMake(null);
                mediaFile.setExifGpsLocation(null);
            });
        }

        return media;
    }

    @Operation(
            operationId = "getMediaDetails",
            summary = "Get media details",
            description = "Returns user-facing file, capture, photo, and video metadata without server filesystem internals.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Media details"),
            @ApiResponse(responseCode = "404", description = "Media not found", content = @Content)
    })
    @GetMapping(value = "/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MediaDetailsDto getMediaDetails(@PathVariable(value = "id") Long id) {
        MediaFile media = mediaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        return MediaDetailsDto.from(media);
    }

    @RequestMapping("/get-media-in-bounds")
    public ResponseEntity<List<MediaPointDTO>> getMediaInBounds(
            @RequestParam("minLat") double minLat,
            @RequestParam("minLng") double minLng,
            @RequestParam("maxLat") double maxLat,
            @RequestParam("maxLng") double maxLng) {
        List<Object[]> raw = mediaRepository.findMediaInBoundsRaw(minLat, minLng, maxLat, maxLng);
        List<MediaPointDTO> points = raw.stream()
                .map(row -> new MediaPointDTO(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).doubleValue()))
                .collect(Collectors.toList());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(points);
    }

    @Operation(
            operationId = "getMediaByTrack",
            summary = "Get the media timeline for an activity",
            description = "Returns one bounded page of persisted activity correlations, original EXIF evidence, route positions, and resolved display positions. " +
                          "A non-zero camera offset produces an unsaved preview; authoritative EXIF GPS time is never shifted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Media matched to the activity in adjusted capture-time order"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Camera offset is outside the supported plus or minus 24-hour range",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Activity not found", content = @Content)
    })
    @GetMapping(value = "/by-track/{trackId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TrackMediaPageDto> getMediaByTrack(
            @PathVariable long trackId,
            @Parameter(description = "Optional unsaved preview offset added to EXIF DateTimeOriginal; zero reads persisted correlations")
            @RequestParam(name = "cameraOffsetSeconds", defaultValue = "0") int cameraOffsetSeconds,
            @Parameter(description = "Zero-based page number")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Items per page, from 1 to " + TrackMediaService.MAX_PAGE_SIZE)
            @RequestParam(name = "pageSize", defaultValue = "" + TrackMediaService.DEFAULT_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(trackMediaService.findByTrackId(trackId, cameraOffsetSeconds, page, pageSize));
    }

    @Operation(
            operationId = "getMediaTrends",
            summary = "Get media counts grouped by capture time",
            description = "Returns filter-aware matched activity media or all indexed media as separate image and video counts. " +
                          "Effective capture time uses EXIF GPS time first, then offset-adjusted EXIF capture time.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Media trend buckets"),
            @ApiResponse(responseCode = "400", description = "The trend request is invalid", content = @Content)
    })
    @PostMapping(value = "/trends", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MediaTrendResponseDto getMediaTrends(@RequestBody MediaTrendRequest request) {
        return mediaTrendService.getTrends(request);
    }

    @Operation(
            operationId = "getMediaTrendItems",
            summary = "Get one page of media for a trend bucket",
            description = "Returns media in stable newest-first order for the selected scope, period and media kind.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged media trend items"),
            @ApiResponse(responseCode = "400", description = "The trend item request is invalid", content = @Content)
    })
    @PostMapping(value = "/trends/items", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MediaTrendItemPageDto getMediaTrendItems(@RequestBody MediaTrendItemsRequest request) {
        return mediaTrendService.getItems(request);
    }

    @Operation(
            operationId = "setManualMediaLocation",
            summary = "Set a user-assigned media location",
            description = "Stores a user-assigned position without changing original EXIF coordinates.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Manual position saved"),
            @ApiResponse(responseCode = "400", description = "Coordinate is invalid", content = @Content),
            @ApiResponse(responseCode = "404", description = "Media not found", content = @Content)
    })
    @PutMapping(value = "/{mediaId}/manual-location", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> setManualLocation(
            @PathVariable long mediaId,
            @RequestBody ManualMediaLocationRequest request) {
        mediaPositionService.setManualLocation(mediaId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            operationId = "clearManualMediaLocation",
            summary = "Clear a user-assigned media location",
            description = "Removes only the user assignment, then resolves the position from EXIF or track correlation.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Manual position cleared"),
            @ApiResponse(responseCode = "404", description = "Media not found", content = @Content)
    })
    @DeleteMapping("/{mediaId}/manual-location")
    public ResponseEntity<Void> clearManualLocation(@PathVariable long mediaId) {
        mediaPositionService.clearManualLocation(mediaId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            operationId = "saveMediaTimeCorrections",
            summary = "Save camera-clock corrections",
            description = "Stores a reversible offset for camera-clock media and immediately rebuilds their track correlations. " +
                          "An offset of zero clears saved corrections. EXIF GPS timestamps are never changed.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eligible camera-time corrections saved or cleared"),
            @ApiResponse(responseCode = "400", description = "Request or offset is invalid", content = @Content),
            @ApiResponse(responseCode = "404", description = "One or more media items were not found", content = @Content)
    })
    @PutMapping(value = "/time-corrections", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> saveTimeCorrections(@RequestBody MediaTimeCorrectionRequest request) {
        mediaPositionService.saveTimeCorrection(request);
        return ResponseEntity.noContent().build();
    }

    @SneakyThrows
    @GetMapping("/get/{id}/content")
    public ResponseEntity<?> getContent(
            @PathVariable(value = "id") Long id,
            @RequestParam(name = "maxSize", required = false) Integer maxSize,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String acceptHeader,
            WebRequest webRequest,
            HttpServletResponse servletResponse)
            throws MalformedURLException {

        MediaFile mediaFile = mediaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));

        Path mediaPath = Paths.get(mediaFile.getIndexedFile().getFullPath());
        long fileSize = Files.size(mediaPath);
        if (fileSize <= EMPTY_MEDIA_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Media file is empty");
        }
        long lastModifiedMillis = Files.getLastModifiedTime(mediaPath).toMillis();
        String fileName = mediaFile.getIndexedFile().getName();
        String lowerName = fileName != null ? fileName.toLowerCase() : "";
        boolean isHeic = lowerName.endsWith(".heic") || lowerName.endsWith(".heif");
        boolean isVideo = lowerName.matches(".*\\.(mp4|mov|m4v|3gp|avi|mkv)$");
        boolean needsHeicConversion = isHeic && !browserAcceptsHeic(acceptHeader);
        boolean needsVideoThumbnail = isVideo && maxSize != null && maxSize > 0;
        String responseVariant = needsHeicConversion
                ? "converted-jpeg"
                : needsVideoThumbnail ? "video-poster-jpeg" : "original";
        String eTag = buildMediaEtag(id, fileSize, lastModifiedMillis, responseVariant, maxSize);

        if ((rangeHeader == null || rangeHeader.isBlank()) && webRequest.checkNotModified(eTag, lastModifiedMillis)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(mediaContentCacheControl())
                    .eTag(eTag)
                    .lastModified(lastModifiedMillis)
                    .build();
        }

        if (needsHeicConversion) {
            serveHeicAsJpeg(mediaPath, fileName, maxSize, eTag, lastModifiedMillis, servletResponse);
            return null; // response already committed
        }

        if (needsVideoThumbnail) {
            serveVideoThumbnail(mediaPath, fileName, maxSize, eTag, lastModifiedMillis, servletResponse);
            return null; // response already committed
        }

        String contentType = resolveContentType(mediaPath, lowerName, isHeic);

        if (isVideo) {
            return serveVideo(mediaPath, fileSize, contentType, rangeHeader, eTag, lastModifiedMillis);
        }

        if (maxSize != null && maxSize > 0) {
            serveResizedImage(mediaPath, fileName, contentType, maxSize, eTag, lastModifiedMillis, servletResponse);
            return null; // response already committed
        }

        return serveImage(mediaPath, contentType, eTag, lastModifiedMillis);
    }

    // ── HEIC → JPEG conversion (synchronous streaming) ────────────────────

    @SneakyThrows
    private void serveHeicAsJpeg(Path mediaPath, String fileName, Integer maxSize,
                                 String eTag, long lastModifiedMillis,
                                 HttpServletResponse response) {
        log.info("HEIC conversion: starting ImageMagick convert for {} (maxSize={})", fileName, maxSize);
        long t0 = System.currentTimeMillis();

        streamGeneratedImageProcessOutput(
                imageMagickProcess(mediaPath, maxSize, "jpeg"),
                response,
                MediaType.IMAGE_JPEG_VALUE,
                eTag,
                lastModifiedMillis);
        log.info("HEIC conversion: finished {} in {}ms", fileName, System.currentTimeMillis() - t0);
    }

    // ── Image resizing via ImageMagick ─────────────────────────────────────

    @SneakyThrows
    private void serveResizedImage(Path mediaPath, String fileName, String contentType, int maxSize,
                                   String eTag, long lastModifiedMillis,
                                   HttpServletResponse response) {
        log.debug("Image resize: starting ImageMagick convert for {} (maxSize={})", fileName, maxSize);
        long t0 = System.currentTimeMillis();

        // Determine output format from content type
        String outputFormat = contentType.contains("png") ? "png" : "jpeg";
        String outputMediaType = outputFormat.equals("png") ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;

        streamGeneratedImageProcessOutput(
                imageMagickProcess(mediaPath, maxSize, outputFormat),
                response,
                outputMediaType,
                eTag,
                lastModifiedMillis);
        log.info("Image resize: finished {} in {}ms", fileName, System.currentTimeMillis() - t0);
    }

    @SneakyThrows
    private void serveVideoThumbnail(Path mediaPath, String fileName, int maxSize,
                                     String eTag, long lastModifiedMillis,
                                     HttpServletResponse response) {
        log.debug("Video thumbnail: extracting frame from {} (maxSize={})", fileName, maxSize);
        long t0 = System.currentTimeMillis();
        byte[] thumbnail = videoThumbnailService.createThumbnail(mediaPath, maxSize);

        prepareGeneratedImageResponse(response, MediaType.IMAGE_JPEG_VALUE, eTag, lastModifiedMillis);
        response.setContentLength(thumbnail.length);
        try (var out = response.getOutputStream()) {
            out.write(thumbnail);
            out.flush();
        }
        log.info("Video thumbnail: generated {} in {}ms", fileName, System.currentTimeMillis() - t0);
    }

    private static ProcessBuilder imageMagickProcess(Path mediaPath, Integer maxSize, String outputFormat) {
        List<String> command = new ArrayList<>(List.of(
                IMAGE_MAGICK_COMMAND,
                mediaPath.toAbsolutePath().toString()));
        if (maxSize != null && maxSize > 0) {
            // ">" suffix = only shrink, never enlarge
            command.addAll(List.of("-resize", maxSize + "x" + maxSize + ">"));
        }
        command.addAll(List.of("-quality", IMAGE_MAGICK_QUALITY, outputFormat + ":-"));
        return new ProcessBuilder(command);
    }

    private static void prepareGeneratedImageResponse(HttpServletResponse response,
                                                      String contentType,
                                                      String eTag,
                                                      long lastModifiedMillis) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.ETAG, eTag);
        response.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModifiedMillis);
        response.setHeader(HttpHeaders.CACHE_CONTROL, mediaContentCacheControl().getHeaderValue());
    }

    private void streamGeneratedImageProcessOutput(ProcessBuilder processBuilder,
                                                   HttpServletResponse response,
                                                   String contentType,
                                                   String eTag,
                                                   long lastModifiedMillis) throws IOException {
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process proc;
        try {
            proc = mediaProcessLimiter.start(processBuilder);
        } catch (MediaProcessLimiter.MediaProcessBusyException e) {
            prepareGeneratedImageErrorResponse(response);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Media processing is busy. Try again shortly.",
                    e);
        } catch (IOException | RuntimeException e) {
            prepareGeneratedImageErrorResponse(response);
            throw e;
        }
        prepareGeneratedImageResponse(response, contentType, eTag, lastModifiedMillis);
        try (var in = proc.getInputStream();
             var out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        } finally {
            proc.destroyForcibly();
        }
    }

    private static void prepareGeneratedImageErrorResponse(HttpServletResponse response) {
        response.reset();
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
    }

    private static boolean browserAcceptsHeic(String acceptHeader) {
        return acceptHeader != null
               && (acceptHeader.contains("image/heic") || acceptHeader.contains("image/heif"));
    }

    // ── Video streaming with byte-range support ─────────────────────────────

    private ResponseEntity<?> serveVideo(Path mediaPath, long fileSize, String contentType,
                                         String rangeHeader, String eTag, long lastModifiedMillis) {
        Resource resource = new FileSystemResource(mediaPath);
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            try {
                List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
                if (!ranges.isEmpty()) {
                    return serveVideoRange(mediaPath, fileSize, contentType, ranges.get(0), eTag, lastModifiedMillis);
                }
            } catch (IllegalArgumentException e) {
                return unsatisfiedVideoRange(fileSize);
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .cacheControl(mediaContentCacheControl())
                .eTag(eTag)
                .lastModified(lastModifiedMillis)
                .contentLength(fileSize)
                .body(resource);
    }

    private ResponseEntity<?> serveVideoRange(Path mediaPath, long fileSize, String contentType,
                                              HttpRange range, String eTag, long lastModifiedMillis) {
        long start = range.getRangeStart(fileSize);
        long end = range.getRangeEnd(fileSize);
        if (start < 0 || start >= fileSize || end < start) return unsatisfiedVideoRange(fileSize);
        long rangeLength = end - start + 1;
        Resource region = new ByteRangeResource(mediaPath, start, rangeLength);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes " + start + "-" + end + "/" + fileSize)
                .cacheControl(mediaContentCacheControl())
                .eTag(eTag)
                .lastModified(lastModifiedMillis)
                .contentLength(rangeLength)
                .body(region);
    }

    private static ResponseEntity<Void> unsatisfiedVideoRange(long fileSize) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                .build();
    }

    // ── Plain image serving ─────────────────────────────────────────────────

    @SneakyThrows
    private ResponseEntity<Resource> serveImage(Path mediaPath, String contentType,
                                                String eTag, long lastModifiedMillis) {
        Resource mediaResource = new UrlResource(mediaPath.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(mediaContentCacheControl())
                .eTag(eTag)
                .lastModified(lastModifiedMillis)
                .body(mediaResource);
    }

    // ── Content type resolution ─────────────────────────────────────────────

    @SneakyThrows
    private static String resolveContentType(Path mediaPath, String lowerName, boolean isHeic) {
        String contentType = Files.probeContentType(mediaPath);
        if (contentType == null) {
            if (isHeic) contentType = "image/heic";
            else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) contentType = "image/jpeg";
            else if (lowerName.endsWith(".png")) contentType = "image/png";
            else if (lowerName.endsWith(".mp4") || lowerName.endsWith(".m4v")) contentType = "video/mp4";
            else if (lowerName.endsWith(".mov")) contentType = "video/quicktime";
            else if (lowerName.endsWith(".3gp")) contentType = "video/3gpp";
            else if (lowerName.endsWith(".avi")) contentType = "video/x-msvideo";
            else if (lowerName.endsWith(".mkv")) contentType = "video/x-matroska";
            else contentType = "application/octet-stream";
        }
        return contentType;
    }

    // ── Caching helpers ─────────────────────────────────────────────────────

    private static CacheControl mediaContentCacheControl() {
        return CacheControl.maxAge(MEDIA_CONTENT_CACHE_HOURS, TimeUnit.HOURS)
                .cachePrivate()
                .mustRevalidate();
    }

    private static String buildMediaEtag(Long mediaId, long fileSize, long lastModifiedMillis,
                                         String variant, Integer maxSize) {
        String sizeTag = (maxSize != null && maxSize > 0) ? "-s" + maxSize : "";
        return "\"media-" + mediaId + '-' + fileSize + '-' + lastModifiedMillis + '-' + variant + sizeTag + "\"";
    }
}
