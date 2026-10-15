package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.SegmentCardiacCostRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostSummary;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentHeartRateCalculator;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentHeartRateSummary;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentTimingStatus;
import com.x8ing.mtl.server.mtlserver.metrics.CanonicalTrackRevision;
import com.x8ing.mtl.server.mtlserver.metrics.chart.ChartPointBudget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentHealthService {
    private static final double FULL_COVERAGE_PERCENT = 100;
    private static final double COVERAGE_ROUNDING_TOLERANCE = 1e-9;
    private final SegmentAttemptResolver resolver;
    private final SegmentCoreService core;
    private final SegmentCardiacCostRepository repository;

    /**
     * One database snapshot keeps canonical motion, gaps and HR consistent within this response. No health cache.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SegmentHealthAnalysis get(String reference, Integer maxBuckets) {
        long started = System.nanoTime();
        var attempt = resolver.resolve(reference);
        var identity = attempt.identity();
        log.info("Segment health analysis starting: trackId={}", identity.trackId());
        try {
            var start = identity.start();
            var end = identity.end();
            if (identity.timingStatus() != SegmentTimingStatus.AVAILABLE || start == null || end == null || !end.isAfter(start)) {
                return new SegmentHealthAnalysis(identity, null, new SegmentHeartRateSummary(null, 0, 0, 0),
                        SegmentCardiacCostCalculator.unavailable(SegmentCardiacCostSummary.UnavailableReason.MISSING_TIMESTAMPS),
                        List.of(), new SegmentMetricAvailability(SegmentMetricAvailability.State.UNAVAILABLE, "TIMING_UNAVAILABLE", null));
            }
            var samples = repository.heartRate(start, end);
            var motion = repository.motion(identity.trackId(), start, end);
            var recordingGaps = repository.recordingGaps(identity.trackId(), start, end);
            var breaks = repository.breaks(identity.trackId(), start, end);
            var full = core.full(attempt);
            var average = SegmentHeartRateCalculator.calculateOverRecordedRanges(start, end, SegmentHealthSeries.recordedRanges(full), samples, recordingGaps);
            var cardiac = SegmentCardiacCostCalculator.motorized(attempt.track().getActivityType())
                    ? SegmentCardiacCostCalculator.unavailable(SegmentCardiacCostSummary.UnavailableReason.MOTORIZED_ACTIVITY)
                    : SegmentCardiacCostCalculator.calculate(start, end, motion, samples, breaks);
            var points = SegmentHealthSeries.build(full, samples, recordingGaps, ChartPointBudget.resolve(maxBuckets));
            var digest = CanonicalTrackRevision.digest();
            for (var sample : samples) CanonicalTrackRevision.add(digest, sample.timestamp(), sample.bpm());
            var state = points.isEmpty() && average.averageBpm() == null ? SegmentMetricAvailability.State.UNAVAILABLE
                    : average.coveragePercent() < FULL_COVERAGE_PERCENT - COVERAGE_ROUNDING_TOLERANCE ? SegmentMetricAvailability.State.PARTIAL : SegmentMetricAvailability.State.AVAILABLE;
            return new SegmentHealthAnalysis(identity, HexFormat.of().formatHex(digest.digest()), average, cardiac, points,
                    new SegmentMetricAvailability(state, state == SegmentMetricAvailability.State.UNAVAILABLE ? "NO_SAMPLES"
                            : state == SegmentMetricAvailability.State.PARTIAL ? "PARTIAL_COVERAGE" : null, average.coveredSeconds()));
        } catch (RuntimeException exception) {
            log.error("Segment health analysis failed: trackId={} type={}", identity.trackId(), exception.getClass().getSimpleName(), exception);
            throw exception;
        } finally {
            log.info("Segment health analysis finished: trackId={} durationMs={}", identity.trackId(), SegmentAttemptResolver.elapsedMillis(started));
        }
    }

}
