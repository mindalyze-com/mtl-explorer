package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import com.x8ing.mtl.server.mtlserver.web.security.DemoWriteAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

import static com.x8ing.mtl.server.mtlserver.trackreview.TrackReviewModels.*;

@RestController
@RequestMapping("/api/track-review")
@RequiredArgsConstructor
@Slf4j
public class SourceReviewController {
    private static final String GPX_MEDIA_TYPE = "application/gpx+xml";
    private final SourceReviewService reviews;
    private final TrackSourceAccess access;
    private final DemoWriteAccess demoWriteAccess;

    @GetMapping
    @Operation(operationId = "getTrackReviewQueue", summary = "List source recordings and pending quality decisions")
    public TrackReviewQueue queue(@RequestParam(required = false) ReviewState state,
                                  @RequestParam(required = false) String search,
                                  @RequestParam(required = false) TrackFinding.Reason reason,
                                  @RequestParam(required = false) TrackFinding.Severity severity,
                                  @io.swagger.v3.oas.annotations.Parameter(description = "Latest recorded decision for a section of the current source version; new findings may still require review.")
                                  @RequestParam(required = false) Decision decision,
                                  @RequestParam(defaultValue = "0") int offset,
                                  @RequestParam(defaultValue = "50") int limit) {
        return reviews.queue(state, search, reason, severity, decision, offset, limit);
    }

    @GetMapping("/{sourceId}")
    @Operation(operationId = "getSourceReview")
    public SourceReviewDetail detail(@PathVariable long sourceId) {
        return reviews.detail(sourceId);
    }

    @GetMapping(value = "/{sourceId}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(operationId = "downloadReviewedOriginal", summary = "Download the original source, including sources that failed parsing")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE, schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<Resource> original(@PathVariable long sourceId) {
        var source = access.locate(sourceId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(source.file().getName(), StandardCharsets.UTF_8).build().toString()).body(new FileSystemResource(source.path()));
    }

    @GetMapping(value = "/{sourceId}/gpx", produces = GPX_MEDIA_TYPE)
    @Operation(operationId = "downloadReviewedGpx", summary = "Download this recording as GPX for correction in another tool")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = GPX_MEDIA_TYPE, schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<? extends Resource> gpx(@PathVariable long sourceId) {
        var location = access.locate(sourceId);
        var format = com.x8ing.mtl.server.mtlserver.gpx.SupportedTrackFormat.fromPath(location.path());
        Resource body = format == com.x8ing.mtl.server.mtlserver.gpx.SupportedTrackFormat.GPX
                ? new FileSystemResource(location.path())
                : new org.springframework.core.io.ByteArrayResource(access.gpx(access.read(sourceId)));
        String name = location.file().getName().replaceFirst("(?i)\\.[^.]+$", "") + ".gpx";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(GPX_MEDIA_TYPE)).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString()).body(body);
    }

    @PostMapping("/{sourceId}/assess")
    @Operation(operationId = "assessTrackSource", summary = "Assess current source bytes without changing or reimporting them")
    public SourceReviewDetail assess(@PathVariable long sourceId) {
        demoWriteAccess.requireImportAndReviewAccess();
        reviews.assess(sourceId);
        return reviews.detail(sourceId);
    }

    @PostMapping("/{sourceId}/decision")
    @Operation(operationId = "decideTrackSourceReview")
    public SourceReviewDetail decide(@PathVariable long sourceId, @RequestBody ReviewDecisionRequest request) {
        demoWriteAccess.requireImportAndReviewAccess();
        reviews.decide(sourceId, request);
        return reviews.detail(sourceId);
    }

    @GetMapping("/{sourceId}/points")
    @Operation(operationId = "getSourceReviewPoints")
    public ReviewPointPage points(@PathVariable long sourceId, @RequestParam String sourceHash,
                                  @RequestParam(defaultValue = "0") int from, @RequestParam(defaultValue = "100") int limit) {
        return reviews.points(sourceId, sourceHash, from, limit);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> invalidRequest(IllegalArgumentException exception) {
        log.warn("Track review request rejected: type={} message={}", exception.getClass().getSimpleName(), exception.getMessage());
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> unavailable(org.springframework.web.server.ResponseStatusException exception) {
        log.warn("Track review request rejected: status={} type={} message={}",
                exception.getStatusCode().value(), exception.getClass().getSimpleName(), exception.getReason());
        return ResponseEntity.status(exception.getStatusCode()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason()));
    }
}
