package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.ChartBucket;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.ChartBucketBuilder;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.MetricKey;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode;
import com.x8ing.mtl.server.mtlserver.metrics.chart.ChartPointBudget;
import com.x8ing.mtl.server.mtlserver.metrics.chart.ChartSeriesMetrics;
import com.x8ing.mtl.server.mtlserver.metrics.window.WindowedRateSample;

import java.util.*;
import java.util.function.IntFunction;

/**
 * Display-only projection. Exact analysis and geometry remain independent of graph settings.
 */
final class SegmentChartSeries {
    private record Bucket(int offset, ChartBucket value) {
    }

    private SegmentChartSeries() {
    }

    static SegmentCoreAnalysis build(SegmentAttemptResolver.ResolvedAttempt attempt, SegmentCoreAnalysis full,
                                     Integer requested, XMode requestedAxis) {
        var distanceAvailability = full.summary().availability().get("distance");
        boolean distanceMismatch = distanceAvailability != null && SegmentMetricAvailability.DISTANCE_MISMATCH.equals(distanceAvailability.reason());
        var metrics = ChartSeriesMetrics.compute(attempt.track(), attempt.points(), null);
        var nodes = SegmentInterval.clip(attempt).nodes();
        List<SegmentAnalysisPoint> display = new ArrayList<>();
        int right = 0;
        for (int i = 0; i < full.points().size(); i++) {
            var original = full.points().get(i);
            double position = nodes.get(i).position();
            while (right < metrics.points().size() - 1 && metrics.points().get(right).getPointIndex() < position) right++;
            Double power = original.powerWatts() == null ? null
                    : at(metrics.points(), position, right, index -> metrics.powerWatts()[index]);
            Double speed = original.elapsedSeconds() == null || distanceMismatch ? null
                    : at(metrics.points(), position, right, index -> metrics.rates().get(index).speedInKmh());
            display.add(new SegmentAnalysisPoint(original.distanceMeters(), original.elapsedSeconds(), original.timestamp(),
                    original.longitude(), original.latitude(), speed, original.altitudeMeters(), power,
                    original.slopePercent(), original.energyWh(), original.breakBefore()));
        }
        XMode axis = attempt.track().isTimingUnreliable() ? XMode.DISTANCE
                : requestedAxis == null ? XMode.TIME : requestedAxis;
        int budget = ChartPointBudget.resolve(requested);
        List<GpsTrackDataPoint> projected = new ArrayList<>();
        List<WindowedRateSample> rates = new ArrayList<>();
        Double[] powers = new Double[display.size()];
        for (int i = 0; i < display.size(); i++) {
            var point = display.get(i);
            GpsTrackDataPoint row = new GpsTrackDataPoint();
            row.setPointIndex(i);
            row.setPointTimestamp(point.timestamp() == null ? null : Date.from(point.timestamp()));
            row.setDurationSinceStart(point.elapsedSeconds());
            row.setDistanceInMeterSinceStart(point.distanceMeters());
            row.setPointAltitude(point.altitudeMeters());
            row.setSlopePercentageInMovingWindow(point.slopePercent());
            row.setEnergyCumulativeWh(point.energyWh());
            if (i > 0 && !point.breakBefore()) {
                var previous = display.get(i - 1);
                row.setDurationBetweenPointsInSec(point.elapsedSeconds() == null || previous.elapsedSeconds() == null
                        ? null : point.elapsedSeconds() - previous.elapsedSeconds());
                row.setDistanceInMeterBetweenPoints(point.distanceMeters() == null || previous.distanceMeters() == null
                        ? null : point.distanceMeters() - previous.distanceMeters());
            }
            projected.add(row);
            rates.add(new WindowedRateSample(point.speedKmh(), null, null));
            powers[i] = point.powerWatts();
        }
        // Split before gaps, coverage transitions and stopped-distance limits. Buckets cannot bridge them.
        List<Bucket> buckets = new ArrayList<>();
        SortedMap<Integer, SegmentAnalysisPoint> output = new TreeMap<>();
        int start = 0;
        for (int end = 1; end <= display.size(); end++) {
            if (end < display.size() && !split(display.get(end - 1), display.get(end))
                && !split(full.points().get(end - 1), full.points().get(end))) continue;
            output.put(start, display.get(start));
            output.put(end - 1, display.get(end - 1));
            int runBudget = Math.max(1, (int) Math.ceil((double) budget * (end - start) / display.size()));
            var result = new ChartBucketBuilder().build(projected.subList(start, end), rates.subList(start, end),
                    Arrays.copyOfRange(powers, start, end), new ChartBucketBuilder.Config(axis, runBudget, null, null, null));
            for (var bucket : result.buckets()) buckets.add(new Bucket(start, bucket));
            start = end;
        }
        var available = buckets.stream().flatMap(b -> b.value().metrics().keySet().stream()).distinct().toList();
        var speedMetric = ChartSeriesMetrics.recommendSpeedMetric(buckets.stream().map(Bucket::value).toList(), available);
        for (var bucket : buckets) {
            var value = bucket.value();
            int index = bucket.offset() + (axis == XMode.DISTANCE ? value.lastPointIndex() : value.representativePointIndex());
            if (output.containsKey(index)) continue; // Exact run boundaries take precedence over bucket averages.
            var anchor = display.get(index);
            output.put(index, new SegmentAnalysisPoint(anchor.distanceMeters(), anchor.elapsedSeconds(), anchor.timestamp(),
                    anchor.longitude(), anchor.latitude(), average(value, speedMetric), average(value, MetricKey.ALTITUDE_M),
                    average(value, MetricKey.POWER_WINDOW_WATTS), average(value, MetricKey.SLOPE_PERCENT),
                    last(value, MetricKey.ENERGY_CUMULATIVE_WH), false));
        }
        return new SegmentCoreAnalysis(full.identity(), full.summary(), List.copyOf(output.values()), full.geometry(), full.sourcePointCount());
    }

    private static Double at(List<GpsTrackDataPoint> points, double position, int right, IntFunction<Double> metric) {
        if (points.get(right).getPointIndex() == position) return metric.apply(right);
        if (right == 0) return null;
        Double a = metric.apply(right - 1), b = metric.apply(right);
        if (!SegmentInterval.finite(a) || !SegmentInterval.finite(b)) return null;
        double leftPosition = points.get(right - 1).getPointIndex();
        return a + (b - a) * (position - leftPosition) / (points.get(right).getPointIndex() - leftPosition);
    }

    private static boolean split(SegmentAnalysisPoint a, SegmentAnalysisPoint b) {
        // Equal known distances preserve stop limits; unknown distance alone is not a new boundary.
        return b.breakBefore() || (a.distanceMeters() != null && Objects.equals(b.distanceMeters(), a.distanceMeters()))
               || (a.distanceMeters() == null) != (b.distanceMeters() == null)
               || (a.speedKmh() == null) != (b.speedKmh() == null)
               || (a.altitudeMeters() == null) != (b.altitudeMeters() == null)
               || (a.powerWatts() == null) != (b.powerWatts() == null)
               || (a.slopePercent() == null) != (b.slopePercent() == null)
               || (a.energyWh() == null) != (b.energyWh() == null);
    }

    private static Double average(ChartBucket bucket, MetricKey metric) {
        var stats = metric == null ? null : bucket.metrics().get(metric);
        return stats == null ? null : stats.getAvg();
    }

    private static Double last(ChartBucket bucket, MetricKey metric) {
        var stats = bucket.metrics().get(metric);
        return stats == null ? null : stats.getLast();
    }
}
