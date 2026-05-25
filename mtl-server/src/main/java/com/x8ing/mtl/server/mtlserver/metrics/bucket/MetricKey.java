package com.x8ing.mtl.server.mtlserver.metrics.bucket;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Catalogue of canonical metric series that the chart bucket builder
 * understands. Keys are exposed as JSON strings so the frontend can
 * subscribe to a subset via the {@code metrics} query parameter.
 */
public enum MetricKey {

    /**
     * Raw point altitude (m).
     */
    ALTITUDE_M("Raw point altitude", "m", 1),

    /**
     * Cumulative distance since track start (m).
     */
    DISTANCE_M("Cumulative distance since track start", "m", 1),

    /**
     * Cumulative duration since track start (s).
     */
    DURATION_S("Cumulative duration since track start", "s", 3),

    /**
     * Cumulative thresholded ascent (m).
     */
    ASCENT_M("Cumulative thresholded ascent", "m", 1),

    /**
     * Cumulative thresholded descent (m).
     */
    DESCENT_M("Cumulative thresholded descent", "m", 1),

    /**
     * Existing 90-second physics moving-window speed (km/h).
     */
    SPEED_MOVING_WINDOW_KMH("Existing 90-second physics moving-window speed", "km/h", 2),

    /**
     * On-the-fly trailing-window average speed (km/h) computed at request
     * time over the canonical RAW_OUTLIER_CLEANED stream.
     */
    SPEED_WINDOW_KMH("On-the-fly trailing-window average speed", "km/h", 2),

    /**
     * On-the-fly trailing-window ascent rate (m/h).
     */
    ELEVATION_GAIN_PER_HOUR_WINDOW("On-the-fly trailing-window ascent rate", "m/h", 0),

    /**
     * On-the-fly trailing-window descent rate (m/h).
     */
    ELEVATION_LOSS_PER_HOUR_WINDOW("On-the-fly trailing-window descent rate", "m/h", 0),

    /**
     * Per-segment slope percentage from the 90-second moving window.
     */
    SLOPE_PERCENT("Per-segment slope percentage from the 90-second moving window", "%", 1),

    /**
     * Instantaneous estimated power (W).
     */
    POWER_WATTS("Instantaneous estimated power", "W", 0),

    /**
     * Trailing-window average power (W) computed at request time.
     */
    POWER_WINDOW_WATTS("Trailing-window average power", "W", 0),

    /**
     * Cumulative net energy since track start (Wh).
     */
    ENERGY_CUMULATIVE_WH("Cumulative net energy since track start", "Wh", 3);

    private final String description;
    private final String unit;
    private final int responseFractionDigits;

    MetricKey(String description, String unit, int responseFractionDigits) {
        this.description = description;
        this.unit = unit;
        this.responseFractionDigits = responseFractionDigits;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    public int getResponseFractionDigits() {
        return responseFractionDigits;
    }

    public MetricBucketStats roundStatsForResponse(MetricBucketStats stats) {
        if (stats == null || stats.getSampleCount() == 0) return stats;
        return new MetricBucketStats(
                roundForResponse(stats.getAvg()),
                roundForResponse(stats.getMin()),
                roundForResponse(stats.getMax()),
                roundForResponse(stats.getFirst()),
                roundForResponse(stats.getLast()),
                stats.getMinPointIndex(),
                stats.getMaxPointIndex(),
                stats.getSampleCount());
    }

    private Double roundForResponse(Double value) {
        if (value == null || !Double.isFinite(value)) return value;
        return BigDecimal.valueOf(value)
                .setScale(responseFractionDigits, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
