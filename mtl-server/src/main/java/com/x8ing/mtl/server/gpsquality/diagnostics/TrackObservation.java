package com.x8ing.mtl.server.gpsquality.diagnostics;

import java.time.Instant;

/**
 * Original order and section identity survive read-only diagnosis.
 */
public record TrackObservation(int pointIndex, int sectionIndex, Double latitude, Double longitude,
                               Double elevationMetres, Instant timestamp, String sourceTimestamp,
                               boolean estimatedTime) {
    public boolean hasPosition() {
        return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
               && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
    }
}
