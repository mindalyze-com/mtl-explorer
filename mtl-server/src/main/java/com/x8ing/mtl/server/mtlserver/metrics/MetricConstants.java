package com.x8ing.mtl.server.mtlserver.metrics;

/**
 * Shared numeric scale and clamping constants for derived motion / elevation
 * metrics. Lives outside any ingest service so that on-the-fly chart series
 * calculations and ingest both reuse the exact same values.
 */
public final class MetricConstants {

    private MetricConstants() {
    }

    public static final double SECONDS_PER_HOUR = 3600.0;
    public static final double MPS_TO_KMH = 3.6;

    /**
     * Default trailing display window for activity-rate charts (seconds).
     */
    public static final double DEFAULT_DISPLAY_WINDOW_SEC = 30.0;

    /**
     * Long-form display window for low-frequency activities like hiking.
     */
    public static final double HIKING_DISPLAY_WINDOW_SEC = 60.0;

    /**
     * Hard upper bound on rendered ascent/descent rate (meters per hour).
     */
    public static final double MAX_ELEVATION_RATE_PER_HOUR = 50_000.0;

    /**
     * Hard upper bound on rendered speed (km/h).
     */
    public static final double MAX_SPEED_KMH = 5_000.0;
}
