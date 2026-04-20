package com.x8ing.mtl.server.mtlserver.planner.dto;

import lombok.Data;

import java.util.List;

/**
 * Route-compute request body. The planner UI sends an ordered list of
 * waypoints and a routing profile. The server computes each leg (pair of
 * consecutive waypoints) via BRouter, optionally re-using cached legs.
 */
@Data
public class RouteRequestDto {
    private List<WaypointDto> waypoints;

    /**
     * One of {@code PlannerProperties.profiles}. Falls back to default when null/unknown.
     */
    private String profile;
}
