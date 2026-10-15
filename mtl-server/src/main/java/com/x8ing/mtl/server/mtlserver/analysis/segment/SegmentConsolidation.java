package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.Segment;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentTimingStatus;

import java.util.*;
import java.util.function.Function;

/**
 * Each arithmetic mean counts only the visits with that metric. HR uses its separate covered-time calculator.
 */
public final class SegmentConsolidation {
    private SegmentConsolidation() {
    }

    private record ZonePair(String from, String to) {
    }

    public static List<SegmentConsolidatedSummary> summarize(List<SegmentAttemptSummary> attempts) {
        Map<ZonePair, List<SegmentAttemptSummary>> groups = new LinkedHashMap<>();
        for (var attempt : attempts)
            groups.computeIfAbsent(new ZonePair(attempt.identity().fromZone(),
                    attempt.identity().toZone()), ignored -> new ArrayList<>()).add(attempt);
        return groups.entrySet().stream().map(entry -> {
            var duration = stats(entry.getValue().stream().filter(SegmentConsolidation::performanceEligible).toList(),
                    SegmentAttemptSummary::elapsedSeconds);
            var distance = stats(entry.getValue(), SegmentAttemptSummary::distanceMeters);
            var speed = stats(entry.getValue(), SegmentAttemptSummary::averageSpeedKmh);
            return new SegmentConsolidatedSummary(entry.getKey().from(), entry.getKey().to(), entry.getValue().size(),
                    (int) duration.getCount(), (int) distance.getCount(), (int) speed.getCount(),
                    mean(duration), mean(distance), mean(speed));
        }).toList();
    }

    /**
     * Raw endpoint elapsed time is inspectable even when path coverage cannot support performance statistics.
     */
    static boolean performanceEligible(SegmentAttemptSummary attempt) {
        var availability = attempt.availability().get(SegmentMetricAvailability.PERFORMANCE_METRIC);
        return availability == null ? attempt.distanceMeters() != null
                : availability.state() == SegmentMetricAvailability.State.AVAILABLE;
    }

    static List<Segment> timingStatistics(List<SegmentAttemptSummary> attempts) {
        Map<ZonePair, Segment> groups = new LinkedHashMap<>();
        for (var attempt : attempts) {
            if (!performanceEligible(attempt) || attempt.identity().timingStatus() != SegmentTimingStatus.AVAILABLE
                || attempt.elapsedSeconds() == null || !Double.isFinite(attempt.elapsedSeconds())) continue;
            var pair = new ZonePair(attempt.identity().fromZone(), attempt.identity().toZone());
            var segment = groups.computeIfAbsent(pair,
                    key -> new Segment(key.from(), key.to(), key.from() + " - " + key.to(), 0, 0d));
            segment.count++;
            segment.timeDelta += attempt.elapsedSeconds();
        }
        return groups.values().stream().sorted(Comparator.comparingInt((Segment segment) -> segment.count).reversed()
                .thenComparing(segment -> segment.point1).thenComparing(segment -> segment.point2)).toList();
    }

    private static DoubleSummaryStatistics stats(List<SegmentAttemptSummary> attempts, Function<SegmentAttemptSummary, Double> metric) {
        return attempts.stream().map(metric).filter(Objects::nonNull).filter(Double::isFinite)
                .mapToDouble(Double::doubleValue).summaryStatistics();
    }

    private static Double mean(DoubleSummaryStatistics stats) {
        return stats.getCount() == 0 ? null : stats.getAverage();
    }
}
