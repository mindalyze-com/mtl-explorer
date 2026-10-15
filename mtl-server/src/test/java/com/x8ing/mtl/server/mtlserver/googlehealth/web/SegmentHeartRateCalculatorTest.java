package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SegmentHeartRateCalculatorTest {
    private static final Instant START = Instant.parse("2099-01-01T12:00:00Z");

    private static Instant at(int seconds) {
        return START.plusSeconds(seconds);
    }

    private static MotionInterval gps(int from, int to) {
        return new MotionInterval(at(from), at(to), com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.STOPPED, null);
    }

    private static HeartRateSample hr(int seconds, double bpm) {
        return new HeartRateSample(at(seconds), bpm);
    }

    @Test
    void weightsIrregularSamplesAndClipsBothBoundariesIncludingStopsWithoutEnergy() {
        var summary = SegmentHeartRateCalculator.calculate(at(5), at(50), List.of(gps(0, 60)),
                List.of(hr(0, 100), hr(10, 140), hr(60, 180)), List.of());
        // 5..10: 120..140; 10..50: 140..172, not a mean of the samples.
        assertThat(summary.averageBpm()).isCloseTo((5 * 130.0 + 40 * 156.0) / 45, within(1e-9));
        assertThat(summary.coveredSeconds()).isEqualTo(45);
        assertThat(summary.coveragePercent()).isEqualTo(100);
    }

    @Test
    void excludesGpsRecordingGapsButKeepsThemInCoverageDenominator() {
        var summary = SegmentHeartRateCalculator.calculate(at(0), at(120), List.of(gps(0, 60), gps(60, 120)),
                List.of(hr(0, 100), hr(60, 160), hr(120, 100)), List.of(new TimeRange(at(40), at(80))));
        assertThat(summary.averageBpm()).isCloseTo(120, within(1e-9));
        assertThat(summary.coveredSeconds()).isEqualTo(80);
        assertThat(summary.coveragePercent()).isCloseTo(100 * 80.0 / 120, within(1e-9));
    }

    @Test
    void doesNotBridgeLongGpsOrHeartRateGapsOrExtrapolate() {
        var gpsGap = SegmentHeartRateCalculator.calculate(at(0), at(120), List.of(gps(0, 120)),
                List.of(hr(0, 140), hr(60, 140), hr(120, 140)), List.of());
        assertThat(gpsGap.averageBpm()).isNull();
        var partial = SegmentHeartRateCalculator.calculate(at(0), at(180), List.of(gps(0, 60), gps(60, 120), gps(120, 180)),
                List.of(hr(10, 140), hr(40, 140), hr(120, 160), hr(150, 160)), List.of());
        assertThat(partial.averageBpm()).isEqualTo(150);
        assertThat(partial.coveredSeconds()).isEqualTo(60);
        assertThat(partial.elapsedSeconds()).isEqualTo(180);
    }

    @Test
    void unavailableAndInvalidSamplesNeverBecomeZeroOrArtificialCoverage() {
        for (var samples : List.of(List.<HeartRateSample>of(), List.of(hr(30, 140)), List.of(hr(0, 140), hr(30, Double.NaN), hr(60, 140)))) {
            var result = SegmentHeartRateCalculator.calculate(at(0), at(60), List.of(gps(0, 60)), samples, List.of());
            assertThat(result.averageBpm()).isNull();
            assertThat(result.coveredSeconds()).isZero();
        }
        assertThat(SegmentHeartRateCalculator.calculate(null, at(60), List.of(), List.of(), List.of()).averageBpm()).isNull();
    }

    @Test
    void combinesVisitsByCoveredTimeIncludingMissingVisitsInCoverage() {
        var result = SegmentHeartRateCalculator.combine(List.of(
                new SegmentHeartRateSummary(100.0, 10, 20, 50),
                new SegmentHeartRateSummary(160.0, 30, 30, 100),
                new SegmentHeartRateSummary(null, 0, 30, 0)));
        assertThat(result.averageBpm()).isEqualTo(145);
        assertThat(result.coveredSeconds()).isEqualTo(40);
        assertThat(result.coveragePercent()).isEqualTo(50);
    }

    @Test
    void duplicateGpsIntervalsDoNotDoubleCount() {
        var result = SegmentHeartRateCalculator.calculate(at(0), at(60), List.of(gps(0, 60), gps(0, 60)),
                List.of(hr(0, 140), hr(60, 140)), List.of());
        assertThat(result.coveredSeconds()).isEqualTo(60);
    }
}
