package com.x8ing.mtl.server.mtlserver.measurement;

/**
 * Earliest track that reached one canonical milestone definition.
 */
public record MilestoneResult(
        MilestoneDimension dimension,
        double thresholdCanonical,
        int sortOrder,
        long trackId,
        double achievedCanonical
) {
}
