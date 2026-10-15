package com.x8ing.mtl.server.gpsquality.diagnostics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Clock evidence shared by source review and the GPX importer. Alerts and containment are independent.
 */
public record RecordingTimingAnalysis(int pointCount, int timedPointCount, int backwardCount,
                                      double maxBackwardSeconds, int excursionCount, int firstExcursionIndex,
                                      Instant earliest, Instant latest, int backwardTransitionCount,
                                      int firstBackwardIndex, int lastBackwardIndex,
                                      List<Integer> isolatedForwardSpikeIndexes, double maxExcursionSeconds) {
    public static final double LARGE_CLOCK_JUMP_SECONDS = 12 * 3600;
    private static final int MIN_EXCURSION_POINTS = 3;
    private static final int MIN_SYSTEMIC_REVERSALS = 3;
    private static final double SYSTEMIC_REVERSAL_RATIO = 0.01;
    private static final double ISOLATED_SPIKE_CADENCE_RATIO = 10;
    private static final double RETURN_CADENCE_RATIO = 3;

    public boolean backwardRequiresReview() {
        return maxBackwardSeconds > GpsReviewThresholds.BACKWARD_SECONDS;
    }

    public String backwardMessage() {
        if (backwardTransitionCount > 0)
            return backwardTransitionCount + " backward clock transition(s) greater than one second leave "
                   + backwardCount + " observations behind an earlier recorded clock maximum.";
        return backwardCount + " observations fall behind an earlier recorded clock maximum. "
               + (backwardRequiresReview() ? "Small reversals accumulate to more than one second."
                                           : "All clock lags are at most one second.");
    }

    public boolean needsContainment() {
        return maxBackwardSeconds >= LARGE_CLOCK_JUMP_SECONDS
               || backwardTransitionCount >= MIN_SYSTEMIC_REVERSALS
                  && backwardTransitionCount >= pointCount * SYSTEMIC_REVERSAL_RATIO
               || excursionCount > isolatedForwardSpikeIndexes.size();
    }

    public static RecordingTimingAnalysis analyze(Iterable<Instant> timestamps) {
        List<Instant> times = new ArrayList<>();
        timestamps.forEach(times::add);
        List<Integer> isolated = new ArrayList<>();
        double maxExcursion = 0;
        for (int i = 2; i < times.size() - 2; i++) {
            double before = TrackDiagnosticEngine.seconds(times.get(i - 2), times.get(i - 1));
            double after = TrackDiagnosticEngine.seconds(times.get(i + 1), times.get(i + 2));
            double resume = TrackDiagnosticEngine.seconds(times.get(i - 1), times.get(i + 1));
            double entry = TrackDiagnosticEngine.seconds(times.get(i - 1), times.get(i));
            double returnLag = TrackDiagnosticEngine.seconds(times.get(i + 1), times.get(i));
            double cadence = Math.max(before, after);
            // Two supported intervals on opposite sides must agree with the return. Neighbours alone
            // cannot establish which clock reading is wrong, and missing points provide no support.
            if (before > 0 && before <= GpsReviewThresholds.SUPPORTED_INTERVAL_SECONDS
                && after > 0 && after <= GpsReviewThresholds.SUPPORTED_INTERVAL_SECONDS
                && resume > 0 && resume <= GpsReviewThresholds.SUPPORTED_INTERVAL_SECONDS * 2
                && resume <= cadence * RETURN_CADENCE_RATIO
                && Math.min(entry, returnLag) > Math.max(GpsReviewThresholds.BACKWARD_SECONDS,
                                                       cadence * ISOLATED_SPIKE_CADENCE_RATIO)) {
                isolated.add(i);
                maxExcursion = Math.max(maxExcursion, Math.max(entry, returnLag));
            }
        }
        var isolatedSet = new HashSet<>(isolated);
        int points = 0, timed = 0, backwards = 0, excursions = 0, firstExcursion = -1;
        int transitions = 0, firstBackward = -1, lastBackward = -1;
        double maxBackward = 0, lastInterval = Double.NaN;
        Instant beforePrevious = null, previous = null, earliest = null, latest = null;
        Instant highWater = null, previousUsable = null;
        for (Instant original : times) {
            Instant current = isolatedSet.contains(points) ? null : original;
            if (original != null) {
                timed++;
                if (earliest == null || original.isBefore(earliest)) earliest = original;
                if (latest == null || original.isAfter(latest)) latest = original;
            }
            if (current != null) {
                if (highWater == null || current.isAfter(highWater)) highWater = current;
                double lag = TrackDiagnosticEngine.seconds(current, highWater);
                maxBackward = Math.max(maxBackward, lag);
                if (lag > 0) {
                    backwards++;
                    if (firstBackward < 0) firstBackward = points;
                    lastBackward = points;
                }
                if (TrackDiagnosticEngine.seconds(current, previousUsable) > GpsReviewThresholds.BACKWARD_SECONDS)
                    transitions++;
                previousUsable = current;
            }
            lastInterval = TrackDiagnosticEngine.seconds(previous, current);
            if (lastInterval < 0) {
                maxBackward = Math.max(maxBackward, -lastInterval);
            }
            if (points >= 2) {
                double entry = TrackDiagnosticEngine.seconds(beforePrevious, previous);
                double resume = TrackDiagnosticEngine.seconds(beforePrevious, current);
                if (Math.abs(entry) > LARGE_CLOCK_JUMP_SECONDS && Math.abs(lastInterval) > LARGE_CLOCK_JUMP_SECONDS
                    && Math.signum(entry) != Math.signum(lastInterval)
                    && resume >= 0 && resume < LARGE_CLOCK_JUMP_SECONDS) {
                    excursions++;
                    maxExcursion = Math.max(maxExcursion, Math.max(Math.abs(entry), Math.abs(lastInterval)));
                    if (firstExcursion < 0) firstExcursion = points - 1;
                }
            }
            beforePrevious = previous;
            previous = current;
            points++;
        }
        if (lastInterval < -LARGE_CLOCK_JUMP_SECONDS && points >= MIN_EXCURSION_POINTS) {
            excursions++;
            maxExcursion = Math.max(maxExcursion, -lastInterval);
            if (firstExcursion < 0) firstExcursion = points - 1;
        }
        if (!isolated.isEmpty()) firstExcursion = firstExcursion < 0 ? isolated.getFirst() : Math.min(firstExcursion, isolated.getFirst());
        return new RecordingTimingAnalysis(points, timed, backwards, maxBackward, excursions + isolated.size(), firstExcursion,
                earliest, latest, transitions, firstBackward, lastBackward, List.copyOf(isolated), maxExcursion);
    }
}
