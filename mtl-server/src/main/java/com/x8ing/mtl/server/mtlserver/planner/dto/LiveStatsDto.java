package com.x8ing.mtl.server.mtlserver.planner.dto;

import lombok.Data;

/**
 * Live-updating stats derived from the currently planned route. All values are
 * cumulative over the whole route (sum of all legs). Fed back to the UI after
 * every route recomputation.
 */
@Data
public class LiveStatsDto {
    /**
     * Total track length in metres (BRouter track-length sum).
     */
    private double distanceM;
    /**
     * Total positive elevation gain, metres (filtered ascend sum).
     */
    private double ascentM;
    /**
     * Total negative elevation loss, metres.
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
