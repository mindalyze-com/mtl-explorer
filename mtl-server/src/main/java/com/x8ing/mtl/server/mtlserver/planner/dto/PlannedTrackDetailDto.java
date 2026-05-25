package com.x8ing.mtl.server.mtlserver.planner.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

/**
 * Full payload of {@code GET /api/planner/plans/{id}} — what the editor needs
 * to re-hydrate a saved plan: the original waypoints (so markers reappear and
 * are draggable), the routed geometry (so the route line shows immediately
 * without waiting for a recompute) and the profile that produced it.
 */
@Data
@JsonPropertyOrder({
        "id",
        "name",
        "description",
        "profile",
        "distanceM",
        "waypoints",
        "coordinates",
        "legs",
        "stats"
})
public class PlannedTrackDetailDto {
    private Long id;
    private String name;
    private String description;
    private String profile;
    private double distanceM;
    /**
     * Original user waypoints, in placement order. May be empty for legacy plans.
     */
    private List<WaypointDto> waypoints;
    /**
     * Routed polyline as {@code [lng, lat, elevationM]} triples.
     */
    private List<double[]> coordinates;
    /**
     * Saved BRouter legs. Empty for legacy plans saved before leg persistence.
     */
    private List<LegResultDto> legs;
    /**
     * Saved aggregate BRouter stats. Derived on load for legacy plans.
     */
    private LiveStatsDto stats;
}
