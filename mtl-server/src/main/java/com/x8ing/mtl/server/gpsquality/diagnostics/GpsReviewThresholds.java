package com.x8ing.mtl.server.gpsquality.diagnostics;

/** Manual review thresholds; observation detection and measurement support remain separate. */
public final class GpsReviewThresholds {
    public static final double BACKWARD_SECONDS = 1;
    public static final double GAP_SECONDS = 12 * 3600;
    public static final double GAP_DISPLACEMENT_METRES = 1000;
    public static final double GAP_DISTANCE_RATIO = 0.05;
    public static final double AFFECTED_RATIO = 0.01;
    public static final int MIN_AFFECTED_POINTS = 10;
    public static final double CORRECTION_METRES = 200;
    public static final double DISTRIBUTED_CORRECTION_METRES = 50;
    public static final double POSITION_SPIKE_METRES = 1000;
    public static final double ELEVATION_SPIKE_METRES = 300;
    public static final double WEAK_FIX_RATIO = 0.10;
    public static final double WEAK_FIX_SECONDS = 300;
    public static final double SUPPORTED_INTERVAL_SECONDS = 120;

    private GpsReviewThresholds() {
    }

    public static boolean substantialCoverage(int affected, int observations) {
        return affected >= MIN_AFFECTED_POINTS && affected >= observations * AFFECTED_RATIO;
    }

}
