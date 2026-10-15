package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostSummary.UnavailableReason;

/**
 * Integrates canonical mechanical work and recorded BPM over exactly the same moving time intervals.
 */
public final class SegmentCardiacCostCalculator {
    public static boolean motorized(com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.ACTIVITY_TYPE type) {
        return type != null && switch (type) {
            case CAR, MOTORBIKING, AIRPLANE, SUPER_SONIC -> true;
            default -> false;
        };
    }

    public static final int MAX_SAMPLE_GAP_SECONDS = 60;
    public static final double MINIMUM_ENERGY_KJ = 1.0;
    private static final double KJ_PER_WH = 3.6;
    private static final double JOULES_PER_KJ = 1_000.0;
    private static final double SECONDS_PER_MINUTE = 60.0;
    private static final double MILLIS_PER_SECOND = 1_000.0;
    private static final double PERCENT_SCALE = 100.0;

    private SegmentCardiacCostCalculator() {
    }

    public record MotionInterval(Instant start, Instant end, TrackMotionAnalyzer.State motionState, Double energyWh) {
    }

    public record HeartRateSample(Instant timestamp, double bpm) {
    }

    public record TimeRange(Instant start, Instant end) {
    }

    private record MovingInterval(Instant start, Instant end, Double energyKjPerSecond) {
    }

    public static SegmentCardiacCostSummary calculate(
            Instant start, Instant end, List<MotionInterval> motion, List<HeartRateSample> samples, List<TimeRange> breaks) {
        if (start == null || end == null || !end.isAfter(start)) return unavailable(UnavailableReason.MISSING_TIMESTAMPS);
        List<TimeRange> excluded = mergeBreaks(breaks);
        List<MovingInterval> moving = new ArrayList<>();
        Instant accountedUntil = start;
        int breakIndex = 0;
        for (MotionInterval interval : motion.stream().filter(point -> point.start() != null && point.end() != null)
                .sorted(Comparator.comparing(MotionInterval::start)).toList()) {
            double seconds = seconds(interval.start(), interval.end());
            if (seconds <= 0 || seconds > MAX_SAMPLE_GAP_SECONDS || interval.motionState() != TrackMotionAnalyzer.State.MOVING) continue;
            Instant left = latest(interval.start(), accountedUntil);
            Instant right = earliest(interval.end(), end);
            if (!right.isAfter(left)) continue;
            accountedUntil = right; // Never count overlapping or duplicate GPS intervals twice.
            Double energyRate = validNonnegative(interval.energyWh()) ? interval.energyWh() * KJ_PER_WH / seconds : null;
            while (breakIndex < excluded.size() && !excluded.get(breakIndex).end().isAfter(left)) breakIndex++;
            for (int i = breakIndex; i < excluded.size() && excluded.get(i).start().isBefore(right); i++) {
                TimeRange gap = excluded.get(i);
                Instant pieceEnd = earliest(gap.start(), right);
                if (pieceEnd.isAfter(left)) moving.add(new MovingInterval(left, pieceEnd, energyRate));
                left = latest(left, gap.end());
                if (!right.isAfter(left)) break;
            }
            if (right.isAfter(left)) moving.add(new MovingInterval(left, right, energyRate));
        }
        double movingSeconds = moving.stream().mapToDouble(piece -> seconds(piece.start(), piece.end())).sum();
        List<HeartRateSample> ordered = samples.stream().filter(sample -> sample.timestamp() != null)
                .sorted(Comparator.comparing(HeartRateSample::timestamp)).toList();
        double coveredSeconds = 0;
        double heartbeats = 0;
        double energyKj = 0;
        int sampleIndex = 0;
        for (MovingInterval interval : moving) {
            if (interval.energyKjPerSecond() == null) continue;
            while (sampleIndex + 1 < ordered.size() && !ordered.get(sampleIndex + 1).timestamp().isAfter(interval.start())) sampleIndex++;
            for (int i = sampleIndex; i + 1 < ordered.size() && ordered.get(i).timestamp().isBefore(interval.end()); i++) {
                HeartRateSample a = ordered.get(i);
                HeartRateSample b = ordered.get(i + 1);
                double sampleSeconds = seconds(a.timestamp(), b.timestamp());
                // Invalid samples break coverage too; do not filter them out and bridge across them.
                if (sampleSeconds <= 0 || sampleSeconds > MAX_SAMPLE_GAP_SECONDS || !validBpm(a.bpm()) || !validBpm(b.bpm())) continue;
                Instant left = latest(interval.start(), a.timestamp());
                Instant right = earliest(interval.end(), b.timestamp());
                if (!right.isAfter(left)) continue;
                double duration = seconds(left, right);
                double leftBpm = a.bpm() + (b.bpm() - a.bpm()) * seconds(a.timestamp(), left) / sampleSeconds;
                double rightBpm = a.bpm() + (b.bpm() - a.bpm()) * seconds(a.timestamp(), right) / sampleSeconds;
                heartbeats += (leftBpm + rightBpm) / 2 * duration / SECONDS_PER_MINUTE;
                energyKj += interval.energyKjPerSecond() * duration;
                coveredSeconds += duration;
            }
        }
        UnavailableReason reason = movingSeconds == 0 ? UnavailableReason.NO_MOVING_DATA
                : coveredSeconds == 0 ? UnavailableReason.NO_MATCHED_DATA
                : energyKj < MINIMUM_ENERGY_KJ ? UnavailableReason.INSUFFICIENT_ENERGY : null;
        return new SegmentCardiacCostSummary(reason == null, reason, reason == null ? heartbeats / energyKj : null,
                // Covered duration cancels when dividing average watts by time-weighted average BPM.
                reason == null ? energyKj * JOULES_PER_KJ / (heartbeats * SECONDS_PER_MINUTE) : null,
                heartbeats, energyKj, coveredSeconds, movingSeconds,
                movingSeconds > 0 ? Math.min(PERCENT_SCALE, coveredSeconds / movingSeconds * PERCENT_SCALE) : 0,
                MINIMUM_ENERGY_KJ);
    }

    public static SegmentCardiacCostSummary unavailable(UnavailableReason reason) {
        return new SegmentCardiacCostSummary(false, reason, null, null, 0, 0, 0, 0, 0, MINIMUM_ENERGY_KJ);
    }

    static List<TimeRange> mergeBreaks(List<TimeRange> breaks) {
        List<TimeRange> merged = new ArrayList<>();
        for (TimeRange range : breaks.stream().filter(r -> r.start() != null && r.end() != null && r.end().isAfter(r.start()))
                .sorted(Comparator.comparing(TimeRange::start)).toList()) {
            if (!merged.isEmpty() && !range.start().isAfter(merged.getLast().end())) {
                TimeRange previous = merged.removeLast();
                merged.add(new TimeRange(previous.start(), latest(previous.end(), range.end())));
            } else merged.add(range);
        }
        return merged;
    }

    private static boolean validNonnegative(Double value) {
        return value != null && Double.isFinite(value) && value >= 0;
    }

    static boolean validBpm(double value) {
        return Double.isFinite(value) && value > 0;
    }

    static double seconds(Instant start, Instant end) {
        return Duration.between(start, end).toMillis() / MILLIS_PER_SECOND;
    }

    static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
