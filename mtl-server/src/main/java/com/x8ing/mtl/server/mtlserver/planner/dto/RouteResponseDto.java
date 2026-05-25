package com.x8ing.mtl.server.mtlserver.planner.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

/**
 * Full route-compute response: per-leg details + rolled-up live stats.
 */
@Data
@JsonPropertyOrder({
        "legs",
        "stats",
        "profile"
})
public class RouteResponseDto {
    private List<LegResultDto> legs;
    private LiveStatsDto stats;
    /**
     * Profile actually used (after fallback to default, if any).
     */
    private String profile;
}
