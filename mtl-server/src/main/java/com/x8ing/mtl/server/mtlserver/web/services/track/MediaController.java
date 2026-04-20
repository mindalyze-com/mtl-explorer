package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaFile;
import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaPointDTO;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private static final int MEDIA_POINTS_CACHE_MINUTES = 3;
    private static final int MEDIA_CONTENT_CACHE_HOURS = 1;

    private final MediaRepository mediaRepository;

    public MediaController(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
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

    @SneakyThrows
    @RequestMapping("/get/{id}")
    public MediaFile getById(@PathVariable(value = "id") Long id) {
        return mediaRepository.findById(id).orElseThrow();
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
                .cacheControl(CacheControl.maxAge(MEDIA_POINTS_CACHE_MINUTES, TimeUnit.MINUTES).cachePublic())
                .body(points);
    }

    @SneakyThrows
    @RequestMapping("/get/{id}/content")
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
        long lastModifiedMillis = Files.getLastModifiedTime(mediaPath).toMillis();
        String fileName = mediaFile.getIndexedFile().getName();
        String lowerName = fileName != null ? fileName.toLowerCase() : "";
        boolean isHeic = lowerName.endsWith(".heic") || lowerName.endsWith(".heif");
        boolean isVideo = lowerName.matches(".*\\.(mp4|mov|m4v|3gp|avi|mkv)$");
        boolean needsHeicConversion = isHeic && !browserAcceptsHeic(acceptHeader);
        String eTag = buildMediaEtag(id, fileSize, lastModifiedMillis, needsHeicConversion, maxSize);

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

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        response.setHeader(HttpHeaders.ETAG, eTag);
        response.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModifiedMillis);
        response.setHeader(HttpHeaders.CACHE_CONTROL, mediaContentCacheControl().getHeaderValue());

        ProcessBuilder pb;
        if (maxSize != null && maxSize > 0) {
            pb = new ProcessBuilder(
                    "convert", mediaPath.toAbsolutePath().toString(),
                    "-resize", maxSize + "x" + maxSize + ">",
                    "-quality", "92", "jpeg:-");
        } else {
            pb = new ProcessBuilder(
                    "convert", mediaPath.toAbsolutePath().toString(),
                    "-quality", "92", "jpeg:-");
        }
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process proc = pb.start();
        try (var in = proc.getInputStream();
             var out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        } finally {
            proc.destroyForcibly();
        }
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

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(outputMediaType);
        response.setHeader(HttpHeaders.ETAG, eTag);
        response.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModifiedMillis);
        response.setHeader(HttpHeaders.CACHE_CONTROL, mediaContentCacheControl().getHeaderValue());

        // ">" suffix = only shrink, never enlarge
        ProcessBuilder pb = new ProcessBuilder(
                "convert", mediaPath.toAbsolutePath().toString(),
                "-resize", maxSize + "x" + maxSize + ">",
                "-quality", "92", outputFormat + ":-");
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process proc = pb.start();
        try (var in = proc.getInputStream();
             var out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        } finally {
            proc.destroyForcibly();
        }
        log.info("Image resize: finished {} in {}ms", fileName, System.currentTimeMillis() - t0);
    }

    private static boolean browserAcceptsHeic(String acceptHeader) {
        return acceptHeader != null
               && (acceptHeader.contains("image/heic") || acceptHeader.contains("image/heif"));
    }

    // ── Video streaming with byte-range support ─────────────────────────────

    private ResponseEntity<StreamingResponseBody> serveVideo(Path mediaPath, long fileSize, String contentType,
                                                             String rangeHeader, String eTag, long lastModifiedMillis) {
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            if (!ranges.isEmpty()) {
                return serveVideoRange(mediaPath, fileSize, contentType, ranges.get(0), eTag, lastModifiedMillis);
            }
        }

        StreamingResponseBody body = out -> Files.copy(mediaPath, out);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .cacheControl(mediaContentCacheControl())
                .eTag(eTag)
                .lastModified(lastModifiedMillis)
                .contentLength(fileSize)
                .body(body);
    }

    private ResponseEntity<StreamingResponseBody> serveVideoRange(Path mediaPath, long fileSize, String contentType,
                                                                  HttpRange range, String eTag, long lastModifiedMillis) {
        long start = range.getRangeStart(fileSize);
        long end = range.getRangeEnd(fileSize);
        long rangeLength = end - start + 1;

        StreamingResponseBody body = out -> {
            try (RandomAccessFile raf = new RandomAccessFile(mediaPath.toFile(), "r")) {
                raf.seek(start);
                byte[] buf = new byte[65536];
                long remaining = rangeLength;
                int read;
                while (remaining > 0 &&
                       (read = raf.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                    out.write(buf, 0, read);
                    remaining -= read;
                }
            }
        };
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes " + start + "-" + end + "/" + fileSize)
                .cacheControl(mediaContentCacheControl())
                .eTag(eTag)
                .lastModified(lastModifiedMillis)
                .contentLength(rangeLength)
                .body(body);
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

    private static String buildMediaEtag(Long mediaId, long fileSize, long lastModifiedMillis, boolean transformedToJpeg, Integer maxSize) {
        String variant = transformedToJpeg ? "jpeg92" : "original";
        String sizeTag = (maxSize != null && maxSize > 0) ? "-s" + maxSize : "";
        return "\"media-" + mediaId + '-' + fileSize + '-' + lastModifiedMillis + '-' + variant + sizeTag + "\"";
    }
}
