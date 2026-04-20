package com.x8ing.mtl.server.mtlserver.planner.dto;

import lombok.Data;

import java.util.List;

/**
 * One leg of a multi-waypoint route — the result of a single BRouter call
 * between two adjacent waypoints.
 */
@Data
public class LegResultDto {
    /**
     * Geometry of this leg as an array of {@code [lng, lat, elevationM]} triples (GeoJSON order).
     */
    private List<double[]> coordinates;
    private double distanceM;
    private double ascentM;
    private double descentM;
    private double durationSec;
    /**
     * True when served from the in-memory per-leg cache rather than a fresh BRouter call.
     */
    private boolean cached;
}
