package com.x8ing.mtl.server.mtlserver.analysis.segment;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Equal traveled distance is a pacing comparison, not a route-position or finishing lead.
 */
public final class SegmentComparisonCalculator {
    public static final String METHOD = "EQUAL_TRAVELED_DISTANCE";

    private SegmentComparisonCalculator() {
    }

    public static SegmentComparison compare(SegmentCoreAnalysis attempt, SegmentCoreAnalysis reference) {
        var a = attempt.summary();
        var r = reference.summary();
        if (!Objects.equals(attempt.identity().zoneRevision(), reference.identity().zoneRevision())
            || !Objects.equals(attempt.identity().fromZone(), reference.identity().fromZone())
            || !Objects.equals(attempt.identity().toZone(), reference.identity().toZone()))
            return unavailable(attempt, reference, "DIFFERENT_ZONE_PAIR");
        if (a.elapsedSeconds() == null || r.elapsedSeconds() == null)
            return unavailable(attempt, reference, "TIMING_OR_DISTANCE_UNAVAILABLE");
        if (a.distanceMeters() == null || r.distanceMeters() == null)
            return new SegmentComparison(attempt.identity(), reference.identity(), METHOD,
                    a.elapsedSeconds() - r.elapsedSeconds(), null, List.of(), List.of(), "DISTANCE_UNAVAILABLE");
        double support = Math.min(a.distanceMeters(), r.distanceMeters());
        SortedSet<Double> distances = new TreeSet<>(List.of(0.0, support));
        attempt.geometry().stream().map(SegmentGeometryPoint::distanceMeters).filter(d -> d != null && d <= support).forEach(distances::add);
        reference.geometry().stream().map(SegmentGeometryPoint::distanceMeters).filter(d -> d != null && d <= support).forEach(distances::add);
        List<SegmentGapPoint> result = new ArrayList<>();
        Double previous = null;
        for (double d : distances) {
            boolean broken = previous != null && (timeAt(attempt.geometry(), (previous + d) / 2, false) == null
                                                  || timeAt(reference.geometry(), (previous + d) / 2, false) == null);
            Double arrival = difference(timeAt(attempt.geometry(), d, false), timeAt(reference.geometry(), d, false));
            result.add(new SegmentGapPoint(d, arrival, broken, "ARRIVAL"));
            Double departure = difference(timeAt(attempt.geometry(), d, true), timeAt(reference.geometry(), d, true));
            // A duplicate x marks the right-hand limit after a stop. It never replaces the exact-distance arrival value.
            if (d < support && !Objects.equals(arrival, departure)) result.add(new SegmentGapPoint(d, departure, true, "DEPARTURE_LIMIT"));
            previous = d;
        }
        return new SegmentComparison(attempt.identity(), reference.identity(), METHOD, a.elapsedSeconds() - r.elapsedSeconds(),
                support, List.copyOf(result), ghosts(attempt, reference, r.distanceMeters()), null);
    }

    /**
     * Samples where the attempt was at the reference's arrival time, over the reference's full timed coverage.
     */
    static List<SegmentGhostPoint> ghosts(SegmentCoreAnalysis attempt, SegmentCoreAnalysis reference, double referenceDistance) {
        var referenceTimeline = Timeline.of(reference.geometry());
        var attemptTimeline = Timeline.of(attempt.geometry());
        if (referenceTimeline.isEmpty() || attemptTimeline.isEmpty()) return List.of();
        SortedSet<Double> distances = new TreeSet<>(List.of(0.0, referenceDistance));
        reference.geometry().stream().map(SegmentGeometryPoint::distanceMeters).filter(d -> d != null && d <= referenceDistance).forEach(distances::add);
        // The attempt's own samples become reference breakpoints, so its pace changes are not smoothed away.
        for (double t : attemptTimeline.times()) {
            Double d = referenceTimeline.distanceAt(t);
            if (d != null && d <= referenceDistance) distances.add(d);
        }
        List<SegmentGhostPoint> result = new ArrayList<>();
        Double previous = null;
        for (double d : distances) {
            boolean broken = previous != null && !ghostAt(attemptTimeline, timeAt(reference.geometry(), (previous + d) / 2, false), d).available();
            Double arrivalTime = timeAt(reference.geometry(), d, false);
            var arrival = ghostAt(attemptTimeline, arrivalTime, d);
            result.add(arrival.point(d, arrivalTime, broken || !arrival.available(), "ARRIVAL"));
            Double departureTime = timeAt(reference.geometry(), d, true);
            // While the reference is stopped, the attempt keeps moving; the duplicate x marks where it was on departure.
            if (d < referenceDistance && arrivalTime != null && departureTime != null && departureTime > arrivalTime) {
                var departure = ghostAt(attemptTimeline, departureTime, d);
                result.add(departure.point(d, departureTime, true, "DEPARTURE_LIMIT"));
            }
            previous = d;
        }
        return List.copyOf(result);
    }

    private record Ghost(Double distance, String state) {
        boolean available() {
            return distance != null;
        }

        SegmentGhostPoint point(double referenceDistance, Double referenceTime, boolean breakBefore, String meaning) {
            return new SegmentGhostPoint(referenceDistance, referenceTime, distance, distance == null ? null : distance - referenceDistance,
                    state, breakBefore, meaning);
        }
    }

    private static Ghost ghostAt(Timeline attempt, Double referenceTime, double referenceDistance) {
        if (referenceTime == null) return new Ghost(null, SegmentGhostPoint.UNAVAILABLE);
        if (referenceTime > attempt.lastTime()) return new Ghost(attempt.lastDistance(), SegmentGhostPoint.FINISHED);
        Double distance = attempt.distanceAt(referenceTime);
        return distance == null ? new Ghost(null, SegmentGhostPoint.UNAVAILABLE) : new Ghost(distance, SegmentGhostPoint.IN_PROGRESS);
    }

    /**
     * Timed geometry samples in order; recording breaks and untimed samples stay unsupported.
     */
    private record Timeline(List<SegmentGeometryPoint> points, int[] indices, double[] times) {
        static Timeline of(List<SegmentGeometryPoint> points) {
            int[] indices = IntStream.range(0, points.size()).filter(i -> points.get(i).elapsedSeconds() != null && points.get(i).distanceMeters() != null).toArray();
            double[] times = Arrays.stream(indices).mapToDouble(i -> points.get(i).elapsedSeconds()).toArray();
            return new Timeline(points, indices, times);
        }

        boolean isEmpty() {
            return times.length == 0;
        }

        double lastTime() {
            return times[times.length - 1];
        }

        double lastDistance() {
            return points.get(indices[indices.length - 1]).distanceMeters();
        }

        /**
         * Distance at an elapsed time; at an exact sample time this is the first sample, matching arrival semantics.
         */
        Double distanceAt(double time) {
            if (isEmpty() || time < times[0] || time > lastTime()) return null;
            int lo = 0, hi = times.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (times[mid] < time) lo = mid + 1;
                else hi = mid;
            }
            var right = points.get(indices[lo]);
            if (times[lo] == time) return right.distanceMeters();
            var left = points.get(indices[lo - 1]);
            if (indices[lo] - indices[lo - 1] != 1 || right.breakBefore() || times[lo] < times[lo - 1]) return null;
            double fraction = (time - times[lo - 1]) / (times[lo] - times[lo - 1]);
            return left.distanceMeters() + (right.distanceMeters() - left.distanceMeters()) * fraction;
        }
    }

    static Double timeAt(List<SegmentGeometryPoint> points, double distance, boolean departure) {
        if (!points.isEmpty() && points.getLast().distanceMeters() == null) {
            int firstUnknown = 0, end = points.size();
            while (firstUnknown < end) {
                int mid = (firstUnknown + end) >>> 1;
                if (points.get(mid).distanceMeters() == null) end = mid;
                else firstUnknown = mid + 1;
            }
            points = points.subList(0, firstUnknown);
        }
        if (points.isEmpty() || distance < points.getFirst().distanceMeters() || distance > points.getLast().distanceMeters()) return null;
        int lo = 0, hi = points.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (points.get(mid).distanceMeters() < distance) lo = mid + 1;
            else hi = mid;
        }
        if (lo == points.size()) return null;
        var right = points.get(lo);
        if (right.distanceMeters() == distance) {
            if (!departure) return right.elapsedSeconds();
            int start = lo, end = points.size();
            while (start < end) {
                int mid = (start + end) >>> 1;
                if (points.get(mid).distanceMeters() <= distance) start = mid + 1;
                else end = mid;
            }
            return points.get(start - 1).elapsedSeconds();
        }
        if (lo == 0) return null;
        var left = points.get(lo - 1);
        if (right.breakBefore() || left.elapsedSeconds() == null || right.elapsedSeconds() == null
            || right.elapsedSeconds() < left.elapsedSeconds()) return null;
        double fraction = (distance - left.distanceMeters()) / (right.distanceMeters() - left.distanceMeters());
        return left.elapsedSeconds() + (right.elapsedSeconds() - left.elapsedSeconds()) * fraction;
    }

    private static Double difference(Double a, Double b) {
        return a == null || b == null ? null : a - b;
    }

    private static SegmentComparison unavailable(SegmentCoreAnalysis attempt, SegmentCoreAnalysis reference, String reason) {
        return new SegmentComparison(attempt.identity(), reference.identity(), METHOD, null, null, List.of(), List.of(), reason);
    }
}
