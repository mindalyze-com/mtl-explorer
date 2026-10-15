package com.x8ing.mtl.server.mtlserver.metrics.chart;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.ChartBucket;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.MetricKey;
import com.x8ing.mtl.server.mtlserver.metrics.window.GpsTrackDataPointWindowAdapter;
import com.x8ing.mtl.server.mtlserver.metrics.window.PointWindowedPowerCalculator;
import com.x8ing.mtl.server.mtlserver.metrics.window.PointWindowedRateCalculator;
import com.x8ing.mtl.server.mtlserver.metrics.window.WindowedRateSample;

import java.util.List;

/**
 * Full-track display metrics, computed before chart ranges or segment boundaries are applied.
 */
public record ChartSeriesMetrics(List<GpsTrackDataPoint> points, List<WindowedRateSample> rates,
                                 Double[] powerWatts, double windowSec) {
    private static final double SPEED_WINDOW_GOOD_COVERAGE = 0.60;
    private static final double SPEED_FALLBACK_COVERAGE_MARGIN = 0.15;
    private static final double COVERAGE_COMPARISON_EPSILON = 1e-12;

    public static ChartSeriesMetrics compute(GpsTrack track, List<GpsTrackDataPoint> canonical, Double requestedWindow) {
        List<GpsTrackDataPoint> points = track.isTimingUnreliable()
                ? canonical.stream().map(ChartSeriesMetrics::geometryOnly).toList() : canonical;
        double window = requestedWindow != null && Double.isFinite(requestedWindow) && requestedWindow > 0
                ? requestedWindow : GpsTrackDataPointWindowAdapter.defaultWindowSecFor(track.getActivityType());
        var rates = new PointWindowedRateCalculator(window).compute(points, GpsTrackDataPointWindowAdapter.view());
        double[] powers = new double[points.size()], durations = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            var point = points.get(i);
            boolean unsupported = point.getIntervalStatus() == GpsTrackDataPoint.IntervalStatus.UNKNOWN
                                  || point.getIntervalStatus() == GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY;
            powers[i] = unsupported || point.getPowerWatts() == null ? Double.NaN : point.getPowerWatts();
            durations[i] = unsupported || point.getDurationBetweenPointsInSec() == null
                    ? Double.NaN : point.getDurationBetweenPointsInSec();
        }
        return new ChartSeriesMetrics(points, rates,
                new PointWindowedPowerCalculator(window).compute(powers, durations).rollingPowerWatts(), window);
    }

    private static GpsTrackDataPoint geometryOnly(GpsTrackDataPoint original) {
        GpsTrackDataPoint point = new GpsTrackDataPoint();
        point.setId(original.getId());
        point.setPointIndex(original.getPointIndex());
        point.setPointAltitude(original.getPointAltitude());
        point.setDistanceInMeterSinceStart(original.getDistanceInMeterSinceStart());
        point.setDistanceInMeterBetweenPoints(original.getDistanceInMeterBetweenPoints());
        point.setAscentInMeterSinceStart(original.getAscentInMeterSinceStart());
        point.setDescentInMeterSinceStart(original.getDescentInMeterSinceStart());
        point.setIntervalStatus(GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY);
        return point;
    }

    public static MetricKey recommendSpeedMetric(List<ChartBucket> buckets, List<MetricKey> availableMetrics) {
        boolean hasWindowSpeed = availableMetrics.contains(MetricKey.SPEED_WINDOW_KMH);
        boolean hasBucketAverageSpeed = availableMetrics.contains(MetricKey.SPEED_BUCKET_AVG_KMH);
        int populatedBuckets = buckets.size();
        if (populatedBuckets == 0) {
            return hasWindowSpeed ? MetricKey.SPEED_WINDOW_KMH : null;
        }

        double windowCoverage = coverage(buckets, MetricKey.SPEED_WINDOW_KMH, populatedBuckets);
        double bucketAverageCoverage = coverage(buckets, MetricKey.SPEED_BUCKET_AVG_KMH, populatedBuckets);

        if (hasWindowSpeed && windowCoverage >= SPEED_WINDOW_GOOD_COVERAGE) {
            return MetricKey.SPEED_WINDOW_KMH;
        }
        if (hasBucketAverageSpeed
            && bucketAverageCoverage + COVERAGE_COMPARISON_EPSILON
               >= windowCoverage + SPEED_FALLBACK_COVERAGE_MARGIN) {
            return MetricKey.SPEED_BUCKET_AVG_KMH;
        }
        if (hasWindowSpeed) {
            return MetricKey.SPEED_WINDOW_KMH;
        }
        if (hasBucketAverageSpeed) {
            return MetricKey.SPEED_BUCKET_AVG_KMH;
        }
        return null;
    }

    private static double coverage(List<ChartBucket> buckets, MetricKey metricKey, int populatedBuckets) {
        long coveredBuckets = buckets.stream()
                .filter(bucket -> bucket.metrics().containsKey(metricKey))
                .count();
        return coveredBuckets / (double) populatedBuckets;
    }
}
