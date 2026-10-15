package com.x8ing.mtl.server.mtlserver.gpx;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Source quality values are evidence, not guaranteed metre accuracy or independent speed.
 */
@JsonPropertyOrder({"sourcePointIndex", "recordingSection", "sourceRecordingSection", "fix", "satellites", "hdop", "vdop", "speedMetresPerSecond", "source", "canonicalPointIndex", "decisions"})
public record GpsObservationEvidence(int sourcePointIndex, int recordingSection, int sourceRecordingSection, String fix,
                                     Integer satellites, Double hdop, Double vdop,
                                     Double speedMetresPerSecond, String source, Integer canonicalPointIndex,
                                     java.util.List<String> decisions) {
    private static final double REFERENCE_DOP = 2;
    private static final double MAX_UNCERTAINTY_SCALE = 10;
    private static final int MIN_FIX_SATELLITES = 4;
    private static final double WEAK_FIX_DOP = 5;

    public boolean weakHorizontalFix() {
        return (hdop != null && hdop > WEAK_FIX_DOP) || weakFix(false);
    }

    public boolean weakVerticalFix() {
        return (vdop != null && vdop > WEAK_FIX_DOP) || weakFix(true);
    }

    private boolean weakFix(boolean vertical) {
        return (satellites != null && satellites < MIN_FIX_SATELLITES) || "none".equalsIgnoreCase(fix)
               || (vertical && "2d".equalsIgnoreCase(fix));
    }

    static java.util.Map<String, String> qualitySettings() {
        return java.util.Map.of("fix.referenceDop", Double.toString(REFERENCE_DOP),
                "fix.maximumUncertaintyScale", Double.toString(MAX_UNCERTAINTY_SCALE),
                "fix.minimumSatellites", Integer.toString(MIN_FIX_SATELLITES),
                "fix.reviewDop", Double.toString(WEAK_FIX_DOP));
    }

    /**
     * DOP is relative geometry quality, not a claimed error radius. Never makes a fix more certain than the configured floor.
     */
    public double uncertaintyScale(boolean vertical) {
        Double dop = vertical ? vdop : hdop;
        double scale = dop != null && Double.isFinite(dop) ? Math.max(1, dop / REFERENCE_DOP) : 1;
        if (weakFix(vertical)) scale = MAX_UNCERTAINTY_SCALE;
        return Math.min(MAX_UNCERTAINTY_SCALE, scale);
    }
}
