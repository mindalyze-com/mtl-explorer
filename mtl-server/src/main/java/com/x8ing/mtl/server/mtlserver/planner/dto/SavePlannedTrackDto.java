package com.x8ing.mtl.server.mtlserver.planner.dto;

import lombok.Data;

/**
 * Body of POST /api/planner/save — persist a planned route as a GpsTrack (PLANNED).
 */
@Data
public class SavePlannedTrackDto {
    private String name;
    private String description;
    /**
     * Profile used when the route was computed (informational, stored in description).
     */
    private String profile;
    /**
     * Full already-routed geometry: {@code [lng, lat, elevationM]} triples.
     */
    private java.util.List<double[]> coordinates;
    /**
     * Original user waypoints (lat/lng pairs in placement order). Persisted so a
     * saved plan can be re-loaded into the editor and reshaped. Optional —
     * older clients that omit this field will save a non-editable plan.
     */
    private java.util.List<WaypointDto> waypoints;
}
