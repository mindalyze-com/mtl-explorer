package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.*;

/**
 * Ordinary segment HR: no speed threshold, energy requirement, or removal of stops.
 */
public final class SegmentHeartRateCalculator {
    private static final double PERCENT_SCALE = 100.0;

    private SegmentHeartRateCalculator() {
    }

    public static SegmentHeartRateSummary calculate(Instant start, Instant end, List<MotionInterval> motion,
                                                    List<HeartRateSample> samples, List<TimeRange> recordingGaps) {
        if (start == null || end == null || !end.isAfter(start)) return summary(0, 0, 0);
        var recorded = mergeBreaks(motion.stream()
                .filter(i -> i.start() != null && i.end() != null && seconds(i.start(), i.end()) > 0
                             && seconds(i.start(), i.end()) <= MAX_SAMPLE_GAP_SECONDS)
                .map(i -> new TimeRange(latest(start, i.start()), earliest(end, i.end()))).toList());
        return calculateOverRecordedRanges(start, end, recorded, samples, recordingGaps);
    }

    /**
     * Canonical support can include a compressed detected stop; BPM interpolation still has its own sample-gap limit.
     */
    public static SegmentHeartRateSummary calculateOverRecordedRanges(Instant start, Instant end, List<TimeRange> recordedRanges,
                                                                      List<HeartRateSample> samples, List<TimeRange> recordingGaps) {
        if (start == null || end == null || !end.isAfter(start)) return summary(0, 0, 0);
        var recorded = mergeBreaks(recordedRanges.stream()
                .map(i -> new TimeRange(latest(start, i.start()), earliest(end, i.end()))).toList());
        var gaps = mergeBreaks(recordingGaps);
        List<TimeRange> usable = new ArrayList<>();
        for (TimeRange range : recorded) {
            Instant left = range.start();
            for (TimeRange gap : gaps) {
                if (!gap.end().isAfter(left)) continue;
                if (!gap.start().isBefore(range.end())) break;
                if (gap.start().isAfter(left)) usable.add(new TimeRange(left, gap.start()));
                left = latest(left, gap.end());
                if (!range.end().isAfter(left)) break;
            }
            if (range.end().isAfter(left)) usable.add(new TimeRange(left, range.end()));
        }
        var ordered = samples.stream().filter(s -> s.timestamp() != null)
                .sorted(Comparator.comparing(HeartRateSample::timestamp)).toList();
        double covered = 0;
        double bpmSeconds = 0;
        int sampleIndex = 0;
        for (TimeRange range : usable) {
            while (sampleIndex + 1 < ordered.size() && !ordered.get(sampleIndex + 1).timestamp().isAfter(range.start())) sampleIndex++;
            for (int i = sampleIndex; i + 1 < ordered.size() && ordered.get(i).timestamp().isBefore(range.end()); i++) {
                var a = ordered.get(i);
                var b = ordered.get(i + 1);
                double dt = seconds(a.timestamp(), b.timestamp());
                if (dt <= 0 || dt > MAX_SAMPLE_GAP_SECONDS || !validBpm(a.bpm()) || !validBpm(b.bpm())) continue;
                Instant left = latest(range.start(), a.timestamp());
                Instant right = earliest(range.end(), b.timestamp());
                if (!right.isAfter(left)) continue;
                double duration = seconds(left, right);
                double leftBpm = a.bpm() + (b.bpm() - a.bpm()) * seconds(a.timestamp(), left) / dt;
                double rightBpm = a.bpm() + (b.bpm() - a.bpm()) * seconds(a.timestamp(), right) / dt;
                bpmSeconds += (leftBpm + rightBpm) / 2 * duration;
                covered += duration;
            }
        }
        return summary(bpmSeconds, covered, seconds(start, end));
    }

    public static SegmentHeartRateSummary combine(List<SegmentHeartRateSummary> visits) {
        double covered = visits.stream().mapToDouble(SegmentHeartRateSummary::coveredSeconds).sum();
        double elapsed = visits.stream().mapToDouble(SegmentHeartRateSummary::elapsedSeconds).sum();
        double weighted = visits.stream().filter(v -> v.averageBpm() != null)
                .mapToDouble(v -> v.averageBpm() * v.coveredSeconds()).sum();
        return summary(weighted, covered, elapsed);
    }

    private static SegmentHeartRateSummary summary(double weighted, double covered, double elapsed) {
        return new SegmentHeartRateSummary(covered > 0 ? weighted / covered : null, covered, elapsed,
                elapsed > 0 ? Math.min(PERCENT_SCALE, covered / elapsed * PERCENT_SCALE) : 0);
    }
}
