package com.x8ing.mtl.server.gpsquality.diagnostics;

import java.time.Instant;
import java.util.List;

public record TrackDiagnosis(String algorithmVersion, String sourceHash, int pointCount, boolean needsReview,
                             boolean estimatedTiming, List<SectionDiagnosis> sections, List<TrackFinding> findings) {
    public record SectionDiagnosis(TrackSection section, int timedPointCount, int backwardJumpCount,
                                   int missingTimeCount, double largestBackwardJumpSeconds,
                                   Instant earliestTimestamp, Instant latestTimestamp, double geometryLengthMetres,
                                   boolean needsReview, boolean estimatedTiming) {
    }
}
