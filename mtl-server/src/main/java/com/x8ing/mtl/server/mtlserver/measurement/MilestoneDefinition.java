package com.x8ing.mtl.server.mtlserver.measurement;

/**
 * A server-owned milestone threshold expressed in its canonical API unit.
 */
public record MilestoneDefinition(
        MilestoneDimension dimension,
        double thresholdCanonical,
        int sortOrder
) {
}
