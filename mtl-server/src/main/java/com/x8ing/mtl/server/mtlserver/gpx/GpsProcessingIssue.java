package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;

/**
 * Persisted with canonical geometry. Both review views present this same import evidence.
 */
public record GpsProcessingIssue(Integer rawPointIndex, Integer canonicalPointIndex, String reason, String rule,
                                 Double horizontalMetres, Double verticalMetres, Double durationSeconds,
                                 TrackFinding finding) {
    /** Historical summaries belong to the removed aggregate metric-impact review rule. */
    public boolean aggregateMetricImpact() {
        return rawPointIndex == null && rule != null && rule.endsWith("-impact");
    }
}
