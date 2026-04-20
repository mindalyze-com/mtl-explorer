package com.x8ing.mtl.server.mtlserver.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single waypoint (lat/lng) supplied by the planner UI.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WaypointDto {
    private double lat;
    private double lng;
}
