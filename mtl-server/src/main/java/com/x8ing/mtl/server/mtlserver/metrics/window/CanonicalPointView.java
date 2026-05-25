package com.x8ing.mtl.server.mtlserver.metrics.window;

/**
 * Lightweight read-only adapter that decouples the windowed metric calculators
 * from any persistence entity. Implementations expose the canonical per-point
 * fields needed for trailing-window aggregations.
 * <p>
 * Production code adapts {@code GpsTrackDataPoint} (the
 * {@code RAW_OUTLIER_CLEANED} variant) into this interface; tests can build
 * synthetic streams directly without spinning up JPA.
 * <p>
 * Methods return primitives — callers are expected to translate missing values
 * into either 0 or the appropriate default <i>before</i> handing the point off
 * to a calculator. This keeps the hot loop branch-free.
 */
public interface CanonicalPointView<P> {

    /**
     * Time (in seconds) elapsed since the previous canonical point.
     */
    double durationBetweenPointsInSec(P point);

    /**
     * Great-circle distance (in metres) since the previous canonical point.
     */
    double distanceInMeterBetweenPoints(P point);

    /**
     * Cumulative thresholded ascent (in metres) from track start up to and
     * including this point. Per-segment ascent is recovered via
     * {@code ascent(i) - ascent(i-1)}.
     */
    double ascentInMeterSinceStart(P point);

    /**
     * Cumulative thresholded descent (in metres) from track start up to and
     * including this point. Mirrors {@link #ascentInMeterSinceStart}.
     */
    double descentInMeterSinceStart(P point);
}
