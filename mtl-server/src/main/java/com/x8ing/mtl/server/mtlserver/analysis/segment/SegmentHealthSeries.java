package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.HeartRateSample;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.TimeRange;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.MAX_SAMPLE_GAP_SECONDS;

/**
 * Interval-only recorded-sample buckets; surrounding support samples are reserved for integration.
 */
final class SegmentHealthSeries {
    private SegmentHealthSeries() {
    }

    static List<TimeRange> recordedRanges(SegmentCoreAnalysis core) {
        List<TimeRange> ranges = new ArrayList<>();
        for (int i = 1; i < core.geometry().size(); i++) {
            var left = core.geometry().get(i - 1);
            var right = core.geometry().get(i);
            if (!right.breakBefore() && left.timestamp() != null && right.timestamp() != null && right.timestamp().isAfter(left.timestamp()))
                ranges.add(new TimeRange(left.timestamp(), right.timestamp()));
        }
        return List.copyOf(ranges);
    }

    static List<SegmentHealthPoint> build(SegmentCoreAnalysis core, List<HeartRateSample> support,
                                          List<TimeRange> gaps, int buckets) {
        var start = core.identity().start();
        var end = core.identity().end();
        if (start == null || end == null || !end.isAfter(start)) return List.of();
        var samples = support.stream().filter(p -> !p.timestamp().isBefore(start) && !p.timestamp().isAfter(end))
                .sorted(Comparator.comparing(HeartRateSample::timestamp)).toList();
        List<SegmentHealthPoint> result = new ArrayList<>();
        List<HeartRateSample> bucket = new ArrayList<>();
        double width = SegmentInterval.seconds(start, end) / buckets;
        int previousBucket = -1;
        Instant previous = null;
        boolean breakBefore = false;
        boolean bucketBreak = false;
        for (var sample : samples) {
            double elapsed = SegmentInterval.seconds(start, sample.timestamp());
            int index = Math.min(buckets - 1, (int) (elapsed / width));
            boolean invalid = !Double.isFinite(sample.bpm()) || sample.bpm() <= 0;
            boolean gap = previous != null && (SegmentInterval.seconds(previous, sample.timestamp()) > MAX_SAMPLE_GAP_SECONDS
                                               || crossesGap(previous, sample.timestamp(), gaps));
            if (invalid || gap || index != previousBucket || breakBefore) {
                emit(core, bucket, bucketBreak, result);
                bucket.clear();
                bucketBreak = breakBefore || gap || invalid;
            }
            if (invalid) breakBefore = true;
            else {
                bucket.add(sample);
                breakBefore = false;
            }
            previous = sample.timestamp();
            previousBucket = index;
        }
        emit(core, bucket, bucketBreak, result);
        return List.copyOf(result);
    }

    private static void emit(SegmentCoreAnalysis core, List<HeartRateSample> samples, boolean breakBefore, List<SegmentHealthPoint> output) {
        if (samples.isEmpty()) return;
        // Average offsets avoids floating loss from averaging large epoch timestamps.
        long firstMillis = samples.getFirst().timestamp().toEpochMilli();
        double offset = samples.stream().mapToLong(s -> s.timestamp().toEpochMilli() - firstMillis).average().orElseThrow();
        Instant timestamp = Instant.ofEpochMilli(firstMillis + Math.round(offset));
        double seconds = SegmentInterval.seconds(core.identity().start(), timestamp);
        Double distance = distanceAt(core.geometry(), seconds);
        output.add(new SegmentHealthPoint(distance, seconds, timestamp,
                samples.stream().mapToDouble(HeartRateSample::bpm).average().orElseThrow(), samples.size(), breakBefore));
    }

    static Double distanceAt(List<SegmentGeometryPoint> geometry, double seconds) {
        for (int i = 0; i < geometry.size(); i++) {
            var right = geometry.get(i);
            if (right.elapsedSeconds() == null) continue;
            if (right.elapsedSeconds() == seconds) return right.distanceMeters();
            if (right.elapsedSeconds() > seconds && i > 0) {
                var left = geometry.get(i - 1);
                if (right.breakBefore() || left.elapsedSeconds() == null || left.elapsedSeconds() > seconds
                    || left.distanceMeters() == null || right.distanceMeters() == null) return null;
                double fraction = (seconds - left.elapsedSeconds()) / (right.elapsedSeconds() - left.elapsedSeconds());
                return left.distanceMeters() + (right.distanceMeters() - left.distanceMeters()) * fraction;
            }
        }
        return null;
    }

    private static boolean crossesGap(Instant left, Instant right, List<TimeRange> gaps) {
        return gaps.stream().anyMatch(g -> g.start().isBefore(right) && g.end().isAfter(left));
    }
}
