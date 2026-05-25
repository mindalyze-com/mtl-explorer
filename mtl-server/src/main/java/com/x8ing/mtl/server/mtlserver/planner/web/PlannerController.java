package com.x8ing.mtl.server.mtlserver.planner.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.planner.client.BRouterException;
import com.x8ing.mtl.server.mtlserver.planner.client.BRouterSegmentMissingException;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.planner.constants.PlannerConstants;
import com.x8ing.mtl.server.mtlserver.planner.dto.*;
import com.x8ing.mtl.server.mtlserver.planner.service.AutoPrewarmService;
import com.x8ing.mtl.server.mtlserver.planner.service.PlannedTrackService;
import com.x8ing.mtl.server.mtlserver.planner.service.PlannerService;
import com.x8ing.mtl.server.mtlserver.planner.service.PlannerStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * HTTP surface of the planner feature. Every endpoint is authenticated by the
 * same JWT filter as the rest of {@code /api/**}. The whole controller is
 * conditional on the planner feature flag so it vanishes entirely when the
 * feature is off.
 */
@Slf4j
@RestController
@RequestMapping(PlannerConstants.PLANNER_API_BASE)
@ConditionalOnProperty(prefix = "mtl.planner", name = "enabled", havingValue = "true")
@JsonPropertyOrder({
        "plannerService",
        "plannedTrackService",
        "plannerStatusService",
        "plannerProperties",
        "autoPrewarmService"
})
public class PlannerController {

    private static final String MIN_LNG = "minLng";
    private static final String MIN_LAT = "minLat";
    private static final String MAX_LNG = "maxLng";
    private static final String MAX_LAT = "maxLat";
    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    private final PlannerService plannerService;
    private final PlannedTrackService plannedTrackService;
    private final PlannerStatusService plannerStatusService;
    private final PlannerProperties plannerProperties;
    private final AutoPrewarmService autoPrewarmService;

    public PlannerController(PlannerService plannerService,
                             PlannedTrackService plannedTrackService,
                             PlannerStatusService plannerStatusService,
                             PlannerProperties plannerProperties,
                             AutoPrewarmService autoPrewarmService) {
        this.plannerService = plannerService;
        this.plannedTrackService = plannedTrackService;
        this.plannerStatusService = plannerStatusService;
        this.plannerProperties = plannerProperties;
        this.autoPrewarmService = autoPrewarmService;
    }

    /**
     * Routing profiles + whether the sidecar is reachable.
     */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "profiles", plannerProperties.getProfiles(),
                "defaultProfile", PlannerConstants.DEFAULT_PROFILE,
                "maxWaypoints", PlannerConstants.MAX_WAYPOINTS
        );
    }

    /**
     * Compute a multi-leg route — the live-stats core.
     */
    @PostMapping("/route")
    public ResponseEntity<?> route(@RequestBody RouteRequestDto req) {
        try {
            RouteResponseDto resp = plannerService.computeRoute(req);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (BRouterSegmentMissingException e) {
            log.warn("BRouter segment missing — triggering on-demand prewarm: {}", e.getMessage());
            autoPrewarmService.triggerPrewarmForBbox(e.getMinLng(), e.getMinLat(), e.getMaxLng(), e.getMaxLat());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "segment-downloading",
                    "detail", "Routing data for this area is being downloaded. Please retry in about 30 seconds."
            ));
        } catch (BRouterException e) {
            log.warn("BRouter failure: {}", e.toString());
            return ResponseEntity.status(502).body(Map.of("error", "routing-unavailable", "detail", e.getMessage()));
        }
    }

    /**
     * Proactive segment download: the client posts the expanded viewport bbox so
     * the sidecar starts downloading tiles before a routing request is made.
     */
    @PostMapping("/prewarm")
    public ResponseEntity<?> prewarm(@RequestBody Map<String, Double> bbox) {
        try {
            validateBbox(bbox);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        double minLng = getRequiredDouble(bbox, MIN_LNG);
        double minLat = getRequiredDouble(bbox, MIN_LAT);
        double maxLng = getRequiredDouble(bbox, MAX_LNG);
        double maxLat = getRequiredDouble(bbox, MAX_LAT);
        autoPrewarmService.triggerPrewarmForBbox(minLng, minLat, maxLng, maxLat);
        return ResponseEntity.accepted().build();
    }

    private void validateBbox(Map<String, Double> bbox) {
        if (bbox == null
            || !bbox.containsKey(MIN_LNG)
            || !bbox.containsKey(MIN_LAT)
            || !bbox.containsKey(MAX_LNG)
            || !bbox.containsKey(MAX_LAT)) {
            throw new IllegalArgumentException("Bounding box must include minLng, minLat, maxLng, and maxLat");
        }
        double minLng = getRequiredDouble(bbox, MIN_LNG);
        double minLat = getRequiredDouble(bbox, MIN_LAT);
        double maxLng = getRequiredDouble(bbox, MAX_LNG);
        double maxLat = getRequiredDouble(bbox, MAX_LAT);
        validateLongitude(minLng);
        validateLongitude(maxLng);
        validateLatitude(minLat);
        validateLatitude(maxLat);
        if (minLng > maxLng || minLat > maxLat) {
            throw new IllegalArgumentException("Bounding box min values must be less than or equal to max values");
        }
    }

    private void validateLatitude(double latitude) {
        if (!Double.isFinite(latitude) || latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
    }

    private void validateLongitude(double longitude) {
        if (!Double.isFinite(longitude) || longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }

    private double getRequiredDouble(Map<String, Double> values, String key) {
        Double value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Bounding box field is required: " + key);
        }
        return value;
    }

    /**
     * BRouter sidecar status — download progress, ready state, etc.
     */
    @GetMapping("/status")
    public BRouterStatusDto status() {
        return plannerStatusService.fetchStatus();
    }

    /**
     * Save the currently-planned track as a PLANNED GpsTrack.
     */
    @PostMapping("/save")
    public ResponseEntity<SavePlannedTrackResponseDto> save(@RequestBody SavePlannedTrackDto dto) {
        try {
            GpsTrack saved = plannedTrackService.save(dto);
            return ResponseEntity.ok(new SavePlannedTrackResponseDto(
                    saved.getId(),
                    saved.getTrackName(),
                    saved.getTrackLengthInMeter() == null ? 0.0 : saved.getTrackLengthInMeter()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * List all saved plans ("My Plans").
     */
    @GetMapping("/plans")
    public List<PlannedTrackSummaryDto> plans() {
        return plannedTrackService.listPlannedTracks();
    }

    /**
     * Full plan payload for re-hydrating the editor (waypoints + routed geometry).
     */
    @GetMapping("/plans/{id}")
    public ResponseEntity<PlannedTrackDetailDto> loadPlan(@PathVariable long id) {
        try {
            return ResponseEntity.ok(plannedTrackService.loadDetail(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete a saved plan. Refuses to touch IMPORTED tracks.
     */
    @DeleteMapping("/plans/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable long id) {
        try {
            plannedTrackService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Download a saved plan as a GPX file.
     */
    @GetMapping("/plans/{id}/gpx")
    public ResponseEntity<byte[]> downloadGpx(@PathVariable long id) {
        try {
            String gpx = plannedTrackService.buildGpx(id);
            byte[] bytes = gpx.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/gpx+xml"));
            headers.setContentDispositionFormData("attachment", "planned-route-" + id + ".gpx");
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
