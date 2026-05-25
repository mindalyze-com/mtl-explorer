package com.x8ing.mtl.server.mtlserver.planner.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

/**
 * Body of POST /api/planner/save — persist a planned route as a GpsTrack (PLANNED).
 */
@Data
@JsonPropertyOrder({
        "name",
        "description",
        "profile",
        "coordinates",
        "waypoints",
        "legs",
        "stats"
})
public class SavePlannedTrackDto {
    private String name;
    private String description;
    /**
     * Profile used when the route was computed (informational, stored in description).
     */
    private String profile;
    /**
     * Full already-routed geometry: {@code [lng, lat, elevationM]} triples.
     * Legacy clients may send only this flattened shape. Newer clients should
     * send {@link #legs} and {@link #stats} as the canonical BRouter result.
     */
    private List<double[]> coordinates;
    /**
     * Original user waypoints (lat/lng pairs in placement order). Persisted so a
     * saved plan can be re-loaded into the editor and reshaped. Optional —
     * older clients that omit this field will save a non-editable plan.
     */
    private List<WaypointDto> waypoints;
    /**
     * Original BRouter legs from the last successful route computation. Persisted
     * so loading a saved plan preserves exact stats and route leg boundaries.
     */
    private List<LegResultDto> legs;
    /**
     * Original aggregate BRouter stats for {@link #legs}.
     */
    private LiveStatsDto stats;
}
