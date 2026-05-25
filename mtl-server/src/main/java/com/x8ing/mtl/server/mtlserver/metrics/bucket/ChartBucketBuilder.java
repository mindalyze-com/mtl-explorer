package com.x8ing.mtl.server.mtlserver.metrics.bucket;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.metrics.window.WindowedRateSample;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pure aggregation of canonical {@link GpsTrackDataPoint} streams into chart
 * buckets. No DB access, no Spring, no entity persistence — all inputs are
 * passed in as plain lists / arrays.
 * <p>
 * The builder partitions the visible x-domain ({@code TIME} or
 * {@code DISTANCE}) into at most {@link Config#maxBuckets()} equal-width
 * buckets, then folds every canonical point into the bucket whose x-range
 * contains its x-coordinate. For each bucket it emits one
 * {@link MetricBucketStats} per registered metric.
 * <p>
 * The set of metrics is determined by {@link MetricKey}. Callers can opt out
 * of metrics via {@code Config.metricFilter} (null means "emit all").
 */
public final class ChartBucketBuilder {

    /**
     * Hard floor on bucket count — we never emit zero buckets for a track that has points.
     */
    public static final int MIN_BUCKETS = 1;
    /**
     * Default {@code maxBuckets} when the caller does not provide one.
     */
    public static final int DEFAULT_MAX_BUCKETS = 1500;

    @JsonPropertyOrder({
            "xMode",
            "maxBuckets",
            "fromX",
            "toX",
            "metricFilter"
    })
    public record Config(XMode xMode, int maxBuckets, Double fromX, Double toX,
                         List<MetricKey> metricFilter) {
        public Config {
            if (xMode == null) xMode = XMode.TIME;
            if (maxBuckets < MIN_BUCKETS) maxBuckets = DEFAULT_MAX_BUCKETS;
        }
    }

    @JsonPropertyOrder({
            "buckets",
            "xStart",
            "xEnd",
            "xMode"
    })
    public record Result(List<ChartBucket> buckets, double xStart, double xEnd, XMode xMode) {
    }

    /**
     * @param points              canonical points, sorted by point_index. Must
     *                            not be modified by the caller during the call.
     * @param windowedRateSamples optional output of
     *                            {@code PointWindowedRateCalculator} — same
     *                            length as {@code points}, or empty / null to
     *                            skip the windowed-rate metrics.
     * @param rollingPowerWatts   optional output of
     *                            {@code PointWindowedPowerCalculator}; same
     *                            length as {@code points}, or null.
     */
    public Result build(List<GpsTrackDataPoint> points,
                        List<WindowedRateSample> windowedRateSamples,
                        Double[] rollingPowerWatts,
                        Config config) {
        if (points == null || points.isEmpty()) {
            return new Result(Collections.emptyList(), 0, 0, config.xMode());
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double[] xs = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            double x = xOf(points.get(i), config.xMode());
            xs[i] = x;
            if (Double.isFinite(x)) {
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
            }
        }
        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || maxX <= minX) {
            return new Result(Collections.emptyList(), 0, 0, config.xMode());
        }

        double xStart = config.fromX() != null && Double.isFinite(config.fromX()) ? config.fromX() : minX;
        double xEnd = config.toX() != null && Double.isFinite(config.toX()) ? config.toX() : maxX;
        if (xEnd <= xStart) {
            xEnd = xStart + 1e-9;
        }

        int nBuckets = Math.max(MIN_BUCKETS, Math.min(config.maxBuckets(), points.size()));
        double bucketWidth = (xEnd - xStart) / nBuckets;
        Map<MetricKey, MetricBucketAccumulator>[] accs = newAccumulatorRow(nBuckets, config.metricFilter());
        int[] firstIdxByBucket = new int[nBuckets];
        int[] lastIdxByBucket = new int[nBuckets];
        java.util.Arrays.fill(firstIdxByBucket, -1);
        java.util.Arrays.fill(lastIdxByBucket, -1);

        for (int i = 0; i < points.size(); i++) {
            double x = xs[i];
            if (!Double.isFinite(x) || x < xStart || x > xEnd) continue;
            int bIdx = bucketIndex(x, xStart, bucketWidth, nBuckets);

            GpsTrackDataPoint p = points.get(i);
            double weight = positive(p.getDurationBetweenPointsInSec());

            Map<MetricKey, MetricBucketAccumulator> row = accs[bIdx];
            offer(row, MetricKey.ALTITUDE_M, p.getPointAltitude(), 1.0, i);
            offer(row, MetricKey.DISTANCE_M, p.getDistanceInMeterSinceStart(), 1.0, i);
            offer(row, MetricKey.DURATION_S, p.getDurationSinceStart(), 1.0, i);
            offer(row, MetricKey.ASCENT_M, p.getAscentInMeterSinceStart(), 1.0, i);
            offer(row, MetricKey.DESCENT_M, p.getDescentInMeterSinceStart(), 1.0, i);
            offer(row, MetricKey.SPEED_MOVING_WINDOW_KMH, p.getSpeedInKmhMovingWindow(), weight, i);
            offer(row, MetricKey.SLOPE_PERCENT, p.getSlopePercentageInMovingWindow(), weight, i);
            offer(row, MetricKey.POWER_WATTS, p.getPowerWatts(), weight, i);
            offer(row, MetricKey.ENERGY_CUMULATIVE_WH, p.getEnergyCumulativeWh(), 1.0, i);

            if (windowedRateSamples != null && i < windowedRateSamples.size() && windowedRateSamples.get(i) != null) {
                WindowedRateSample ws = windowedRateSamples.get(i);
                offer(row, MetricKey.SPEED_WINDOW_KMH, ws.speedInKmh(), weight, i);
                offer(row, MetricKey.ELEVATION_GAIN_PER_HOUR_WINDOW, ws.elevationGainPerHour(), weight, i);
                offer(row, MetricKey.ELEVATION_LOSS_PER_HOUR_WINDOW, ws.elevationLossPerHour(), weight, i);
            }
            if (rollingPowerWatts != null && i < rollingPowerWatts.length) {
                offer(row, MetricKey.POWER_WINDOW_WATTS, rollingPowerWatts[i], weight, i);
            }

            if (firstIdxByBucket[bIdx] < 0) firstIdxByBucket[bIdx] = i;
            lastIdxByBucket[bIdx] = i;
        }

        List<ChartBucket> buckets = new ArrayList<>(nBuckets);
        for (int b = 0; b < nBuckets; b++) {
            if (firstIdxByBucket[b] < 0) continue;
            double bxStart = xStart + b * bucketWidth;
            double bxEnd = b == nBuckets - 1 ? xEnd : bxStart + bucketWidth;
            Map<MetricKey, MetricBucketStats> stats = new LinkedHashMap<>();
            for (Map.Entry<MetricKey, MetricBucketAccumulator> e : accs[b].entrySet()) {
                MetricBucketStats s = e.getKey().roundStatsForResponse(e.getValue().finish());
                if (s.getSampleCount() > 0) stats.put(e.getKey(), s);
            }
            int repIdx = (firstIdxByBucket[b] + lastIdxByBucket[b]) / 2;
            buckets.add(new ChartBucket(
                    b,
                    config.xMode().roundForResponse(bxStart),
                    config.xMode().roundForResponse(bxEnd),
                    firstIdxByBucket[b],
                    lastIdxByBucket[b],
                    repIdx,
                    timestampUtcSecond(points, firstIdxByBucket[b]),
                    timestampUtcSecond(points, lastIdxByBucket[b]),
                    timestampUtcSecond(points, repIdx),
                    stats));
        }
        return new Result(
                buckets,
                config.xMode().roundForResponse(xStart),
                config.xMode().roundForResponse(xEnd),
                config.xMode());
    }

    private static Instant timestampUtcSecond(List<GpsTrackDataPoint> points, int pointIndex) {
        if (pointIndex < 0 || pointIndex >= points.size()) return null;
        Date timestamp = points.get(pointIndex).getPointTimestamp();
        if (timestamp == null) return null;
        return timestamp.toInstant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static double xOf(GpsTrackDataPoint p, XMode xMode) {
        Double v = xMode == XMode.DISTANCE ? p.getDistanceInMeterSinceStart() : p.getDurationSinceStart();
        return v != null && Double.isFinite(v) ? v : Double.NaN;
    }

    private static int bucketIndex(double x, double xStart, double bucketWidth, int nBuckets) {
        if (bucketWidth <= 0) return 0;
        int b = (int) Math.floor((x - xStart) / bucketWidth);
        if (b < 0) return 0;
        if (b >= nBuckets) return nBuckets - 1;
        return b;
    }

    private static double positive(Double v) {
        return v != null && Double.isFinite(v) && v > 0 ? v : 1.0;
    }

    @SuppressWarnings("unchecked")
    private static Map<MetricKey, MetricBucketAccumulator>[] newAccumulatorRow(int nBuckets, List<MetricKey> filter) {
        Map<MetricKey, MetricBucketAccumulator>[] arr = new Map[nBuckets];
        for (int i = 0; i < nBuckets; i++) {
            Map<MetricKey, MetricBucketAccumulator> row = new LinkedHashMap<>();
            for (MetricKey key : MetricKey.values()) {
                if (filter == null || filter.contains(key)) {
                    row.put(key, new MetricBucketAccumulator());
                }
            }
            arr[i] = row;
        }
        return arr;
    }

    private static void offer(Map<MetricKey, MetricBucketAccumulator> row,
                              MetricKey key, Double value, double weight, int pointIndex) {
        MetricBucketAccumulator acc = row.get(key);
        if (acc == null) return;
        acc.add(value, weight, pointIndex);
    }
}
