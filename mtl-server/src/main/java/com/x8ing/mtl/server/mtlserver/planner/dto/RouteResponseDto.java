package com.x8ing.mtl.server.mtlserver.planner.dto;

import lombok.Data;

import java.util.List;

/**
 * Full route-compute response: per-leg details + rolled-up live stats.
 */
@Data
public class RouteResponseDto {
    private List<LegResultDto> legs;
    private LiveStatsDto stats;
    /**
     * Profile actually used (after fallback to default, if any).
     */
    private String profile;
}
