package com.x8ing.mtl.server.mtlserver.planner.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

/**
 * Live-updating stats derived from the currently planned route. All values are
 * cumulative over the whole route. Elevation is filtered across leg boundaries. Fed back to the UI after
 * every route recomputation.
 */
@Data
@JsonPropertyOrder({
        "distanceM",
        "ascentM",
        "descentM",
        "durationSec",
        "legCount",
        "anyLegCached"
})
public class LiveStatsDto {
    /**
     * Total track length in metres (BRouter track-length sum).
     */
    private double distanceM;
    /**
     * Buffered positive elevation gain over the whole route, metres.
     */
    private double ascentM;
    /**
     * Buffered elevation loss over the same geometry in reverse, metres.
     */
    private double descentM;
    /**
     * BRouter estimated duration, seconds.
     */
    private double durationSec;
    /**
     * Number of legs included in the totals.
     */
    private int legCount;
    /**
     * True when at least one leg was served from cache.
     */
    private boolean anyLegCached;
}
