package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/tracks")
@JsonPropertyOrder({
        "trackHealthService"
})
public class TrackHealthController {

    private static final int TRACK_HEALTH_CACHE_SECONDS = 60;

    private final TrackHealthService trackHealthService;

    public TrackHealthController(TrackHealthService trackHealthService) {
        this.trackHealthService = trackHealthService;
    }

    @Operation(
            operationId = "getTrackHealth",
            summary = "Get Google Health data whose timestamps overlap a GPS track")
    @GetMapping("/{trackId}/health")
    public ResponseEntity<TrackHealthResponse> getTrackHealth(
            @PathVariable long trackId,
            @RequestParam(name = "maxBuckets", required = false) Integer maxBuckets) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(TRACK_HEALTH_CACHE_SECONDS, TimeUnit.SECONDS)
                        .cachePrivate()
                        .mustRevalidate())
                .body(trackHealthService.get(trackId, maxBuckets));
    }
}
