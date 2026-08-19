package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaFile;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VideoTranscodeQuality;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VideoTranscodeSessionDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VideoTranscodeSessionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class VideoTranscodeController {

    private static final String X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

    private final MediaRepository mediaRepository;
    private final VideoTranscodeSessionService sessionService;

    public VideoTranscodeController(MediaRepository mediaRepository,
                                    VideoTranscodeSessionService sessionService) {
        this.mediaRepository = mediaRepository;
        this.sessionService = sessionService;
    }

    @Operation(
            operationId = "createVideoTranscodeSession",
            summary = "Create or reconnect to a temporary compatible video stream",
            description = "Starts a bounded speed-first HLS conversion. The same media revision and quality reuses an active or recent completed session. " +
                          "AUTO remuxes compatible H.264/AAC streams at their original size, and otherwise uses the server's 720p fallback cap. " +
                          "The playlist URL is relative to the configured backend base URL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Existing compatible playback session reused"),
            @ApiResponse(responseCode = "201", description = "Compatible playback session created"),
            @ApiResponse(responseCode = "404", description = "Media not found", content = @Content),
            @ApiResponse(responseCode = "422", description = "Media cannot be converted within the selected profile or size limit", content = @Content),
            @ApiResponse(responseCode = "429", description = "The configured transcode concurrency limit is active", content = @Content),
            @ApiResponse(responseCode = "507", description = "Temporary video storage cannot fit the requested session", content = @Content),
            @ApiResponse(responseCode = "503", description = "Video transcoding is disabled or unavailable", content = @Content)
    })
    @PostMapping(
            value = "/{mediaId}/transcode-sessions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VideoTranscodeSessionDto> create(
            @PathVariable long mediaId,
            @RequestBody(required = false) VideoTranscodeSessionRequest request) {
        MediaFile media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        Path source = sourcePath(media);
        VideoTranscodeQuality quality = request == null
                ? VideoTranscodeQuality.AUTO
                : request.effectiveQuality();
        VideoTranscodeSessionService.CreateResult result = sessionService.create(mediaId, source, quality);
        return ResponseEntity.status(result.reused() ? HttpStatus.OK : HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(result.session());
    }

    @Operation(
            operationId = "getVideoTranscodeSession",
            summary = "Get compatible video conversion status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current conversion status"),
            @ApiResponse(responseCode = "404", description = "Session not found or expired", content = @Content)
    })
    @GetMapping(value = "/transcode-sessions/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VideoTranscodeSessionDto> get(@PathVariable UUID sessionId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sessionService.get(sessionId));
    }

    @Operation(
            operationId = "cancelVideoTranscodeSession",
            summary = "Cancel compatible video playback and remove its temporary output")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session cancelled and temporary output removed"),
            @ApiResponse(responseCode = "404", description = "Session not found or expired", content = @Content)
    })
    @DeleteMapping("/transcode-sessions/{sessionId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID sessionId) {
        sessionService.cancel(sessionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            operationId = "getVideoTranscodePlaylist",
            summary = "Read the temporary HLS playlist")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "HLS event playlist"),
            @ApiResponse(responseCode = "409", description = "The first complete segment is not ready", content = @Content),
            @ApiResponse(responseCode = "404", description = "Session not found or expired", content = @Content),
            @ApiResponse(responseCode = "410", description = "Session failed or was cancelled", content = @Content)
    })
    @GetMapping("/transcode-sessions/{sessionId}/playlist.m3u8")
    public ResponseEntity<Resource> playlist(@PathVariable UUID sessionId) {
        return output(sessionId, VideoTranscodeSessionService.PLAYLIST_FILE);
    }

    @Operation(
            operationId = "getVideoTranscodeOutput",
            summary = "Read one temporary HLS initialization or media segment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "HLS initialization or media segment"),
            @ApiResponse(responseCode = "404", description = "Session or segment not found", content = @Content),
            @ApiResponse(responseCode = "410", description = "Session failed or was cancelled", content = @Content)
    })
    @GetMapping("/transcode-sessions/{sessionId}/{fileName:.+}")
    public ResponseEntity<Resource> output(@PathVariable UUID sessionId, @PathVariable String fileName) {
        VideoTranscodeSessionService.OutputResource output = sessionService.getOutput(sessionId, fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(output.contentType()))
                .contentLength(output.contentLength())
                .cacheControl(CacheControl.noStore())
                .header(X_CONTENT_TYPE_OPTIONS_HEADER, "nosniff")
                .body(new FileSystemResource(output.path()));
    }

    private static Path sourcePath(MediaFile media) {
        IndexedFile indexedFile = media.getIndexedFile();
        if (indexedFile == null || indexedFile.getFullPath() == null || indexedFile.getFullPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Media file path is missing");
        }
        return Path.of(indexedFile.getFullPath());
    }
}
