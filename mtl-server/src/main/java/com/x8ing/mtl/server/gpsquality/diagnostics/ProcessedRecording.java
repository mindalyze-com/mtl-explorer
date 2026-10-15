package com.x8ing.mtl.server.gpsquality.diagnostics;

import java.time.Instant;

/**
 * Neutral evidence supplied by the importer or its persistence adapter. Null section means unknown mapping.
 */
public record ProcessedRecording(String sourceHash, Integer sectionIndex, Instant startTimestamp,
                                 Instant endTimestamp, Double movingDurationSeconds, Double distanceMetres) {
}
