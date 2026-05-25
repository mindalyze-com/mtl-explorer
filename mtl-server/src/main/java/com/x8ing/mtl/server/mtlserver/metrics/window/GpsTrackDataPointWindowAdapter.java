package com.x8ing.mtl.server.mtlserver.metrics.window;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.metrics.MetricConstants;

/**
 * Bridges the {@link GpsTrackDataPoint} JPA entity to the abstract
 * {@link CanonicalPointView} consumed by the windowed metric calculators.
 * <p>
 * Kept in its own file so that the calculators stay JPA-free. Also hosts the
 * activity-type → default display window resolution used by both ingest and
 * the chart-series endpoint.
 */
public final class GpsTrackDataPointWindowAdapter {

    private GpsTrackDataPointWindowAdapter() {
    }

    private static final CanonicalPointView<GpsTrackDataPoint> VIEW = new CanonicalPointView<>() {
        @Override
        public double durationBetweenPointsInSec(GpsTrackDataPoint p) {
            Double v = p.getDurationBetweenPointsInSec();
            return v != null && Double.isFinite(v) ? v : 0;
        }

        @Override
        public double distanceInMeterBetweenPoints(GpsTrackDataPoint p) {
            Double v = p.getDistanceInMeterBetweenPoints();
            return v != null && Double.isFinite(v) ? v : 0;
        }

        @Override
        public double ascentInMeterSinceStart(GpsTrackDataPoint p) {
            Double v = p.getAscentInMeterSinceStart();
            return v != null && Double.isFinite(v) ? v : 0;
        }

        @Override
        public double descentInMeterSinceStart(GpsTrackDataPoint p) {
            Double v = p.getDescentInMeterSinceStart();
            return v != null && Double.isFinite(v) ? v : 0;
        }
    };

    public static CanonicalPointView<GpsTrackDataPoint> view() {
        return VIEW;
    }

    /**
     * Resolve the default trailing-window length (seconds) for the given
     * activity type. Slow-motion activities use a longer window so the
     * displayed series feels smooth at 1-Hz sampling.
     */
    public static double defaultWindowSecFor(GpsTrack.ACTIVITY_TYPE activityType) {
        if (activityType == null) {
            return MetricConstants.DEFAULT_DISPLAY_WINDOW_SEC;
        }
        return switch (activityType) {
            case HIKING, WALKING -> MetricConstants.HIKING_DISPLAY_WINDOW_SEC;
            default -> MetricConstants.DEFAULT_DISPLAY_WINDOW_SEC;
        };
    }
}
