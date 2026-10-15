package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostCalculator.*;
import static com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostSummary.UnavailableReason.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SegmentCardiacCostCalculatorTest {
    private static final Instant START = Instant.parse("2099-01-01T12:00:00Z");

    @Test
    void integratesHeartbeatsAndWorkRatherThanDividingAverageHeartRateByEnergy() {
        var result = calculate(START, time(600), motion(600, 250), samples(600, 150), List.of());
        assertThat(result.available()).isTrue();
        assertThat(result.estimatedHeartbeats()).isCloseTo(1500, within(1e-8));
        assertThat(result.mechanicalEnergyKj()).isCloseTo(150, within(1e-8));
        assertThat(result.beatsPerKj()).isCloseTo(10, within(1e-8));
        assertThat(result.wattsPerBpm()).isCloseTo(250.0 / 150, within(1e-8));
        assertThat(result.coveragePercent()).isEqualTo(100);
        var shorter = calculate(START, time(300), motion(600, 250), samples(600, 150), List.of());
        assertThat(shorter.beatsPerKj()).isCloseTo(result.beatsPerKj(), within(1e-8));
        assertThat(shorter.wattsPerBpm()).isCloseTo(result.wattsPerBpm(), within(1e-8));
    }

    @Test
    void usesTimeWeightedHeartbeatsWithIrregularSamplesAndClipsBothSignalsAtSegmentBoundaries() {
        var result = calculate(time(10), time(50), motion(60, 200),
                List.of(hr(0, 120), hr(15, 135), hr(60, 180)), List.of());
        assertThat(result.estimatedHeartbeats()).isCloseTo(100, within(1e-8));
        assertThat(result.mechanicalEnergyKj()).isCloseTo(8, within(1e-8));
        assertThat(result.beatsPerKj()).isCloseTo(12.5, within(1e-8));
        assertThat(result.wattsPerBpm()).isCloseTo(200.0 / 150, within(1e-8));
        assertThat(result.coveredMovingSeconds()).isEqualTo(40);
    }

    @Test
    void limitsEnergyToTheSamePartialCoverageAndNeverExtrapolatesPastSamples() {
        var result = calculate(START, time(120), motion(120, 200), List.of(hr(30, 120), hr(90, 120)), List.of());
        assertThat(result.movingSeconds()).isEqualTo(120);
        assertThat(result.coveredMovingSeconds()).isEqualTo(60);
        assertThat(result.coveragePercent()).isEqualTo(50);
        assertThat(result.mechanicalEnergyKj()).isCloseTo(12, within(1e-8));
        assertThat(result.estimatedHeartbeats()).isEqualTo(120);
        assertThat(result.wattsPerBpm()).isCloseTo(200.0 / 120, within(1e-8));
    }

    @Test
    void subtractsOverlappingStopsAndGpsEventsFromBothSignals() {
        var result = calculate(START, time(120), motion(120, 200), samples(120, 120),
                List.of(new TimeRange(time(20), time(40)), new TimeRange(time(30), time(50)), new TimeRange(time(80), time(100))));
        assertThat(result.movingSeconds()).isEqualTo(70);
        assertThat(result.coveredMovingSeconds()).isEqualTo(70);
        assertThat(result.estimatedHeartbeats()).isEqualTo(140);
        assertThat(result.mechanicalEnergyKj()).isCloseTo(14, within(1e-8));
    }

    @Test
    void doesNotBridgeHeartRateRecordingGapsOrInvalidSamples() {
        var result = calculate(START, time(180), motion(180, 200),
                List.of(hr(0, 120), hr(30, 120), hr(120, 120), hr(150, 120), hr(160, 0), hr(180, 120)), List.of());
        assertThat(result.coveredMovingSeconds()).isEqualTo(60);
        assertThat(result.coveragePercent()).isCloseTo(100.0 / 3, within(1e-8));
        assertThat(result.mechanicalEnergyKj()).isCloseTo(12, within(1e-8));
    }

    @Test
    void excludesStoppedUnknownAndLongGpsIntervals() {
        var result = calculate(START, time(240), List.of(
                new MotionInterval(START, time(30), com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.MOVING, 1.0),
                new MotionInterval(time(30), time(60), com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.STOPPED, 1.0),
                new MotionInterval(time(60), time(90), com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.UNKNOWN, 1.0),
                new MotionInterval(time(90), time(240), com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.MOVING, 50.0)), samples(240, 120), List.of());
        assertThat(result.movingSeconds()).isEqualTo(30);
        assertThat(result.estimatedHeartbeats()).isEqualTo(60);
        assertThat(result.mechanicalEnergyKj()).isEqualTo(3.6);
    }

    @Test
    void reportsMissingEnergyAsUncoveredInsteadOfTreatingItAsZeroWork() {
        var result = calculate(START, time(60), List.of(
                new MotionInterval(START, time(30), com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.MOVING, null),
                new MotionInterval(time(30), time(60), com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.MOVING, 1.0)), samples(60, 120), List.of());
        assertThat(result.coveragePercent()).isEqualTo(50);
        assertThat(result.estimatedHeartbeats()).isEqualTo(60);
        assertThat(result.mechanicalEnergyKj()).isEqualTo(3.6);
    }

    @Test
    void includesMovingCoastingButRejectsInsufficientEnergyWithoutDividingByZero() {
        var zero = calculate(START, time(60), motion(60, 0), samples(60, 120), List.of());
        assertThat(zero.unavailableReason()).isEqualTo(INSUFFICIENT_ENERGY);
        assertThat(zero.beatsPerKj()).isNull();
        assertThat(zero.wattsPerBpm()).isNull();
        assertThat(zero.estimatedHeartbeats()).isEqualTo(120);
        assertThat(zero.coveragePercent()).isEqualTo(100);
        var small = calculate(START, time(60), motion(60, 1), samples(60, 120), List.of());
        assertThat(small.unavailableReason()).isEqualTo(INSUFFICIENT_ENERGY);
        assertThat(small.wattsPerBpm()).isNull();
    }

    @Test
    void dividesTimeWeightedAveragesInsteadOfAveragingInstantaneousRatios() {
        var result = calculate(START, time(60), List.of(
                        new MotionInterval(START, time(30), com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.MOVING, 100.0 * 30 / 3600),
                        new MotionInterval(time(30), time(60), com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.MOVING, 300.0 * 30 / 3600)),
                List.of(hr(0, 100), hr(30, 100), hr(60, 200)), List.of());
        assertThat(result.wattsPerBpm()).isCloseTo(200.0 / 125, within(1e-8));
        assertThat(result.beatsPerKj() * result.wattsPerBpm()).isCloseTo(1000.0 / 60, within(1e-8));
    }

    @Test
    void neverReturnsPowerPerHeartRateForUnavailableSummaries() {
        for (var reason : SegmentCardiacCostSummary.UnavailableReason.values()) {
            assertThat(unavailable(reason).wattsPerBpm()).isNull();
        }
        assertThat(calculate(START, time(60), motion(60, 200), List.of(), List.of()).wattsPerBpm()).isNull();
        assertThat(calculate(START, time(60), List.of(), samples(60, 120), List.of()).wattsPerBpm()).isNull();
    }

    @Test
    void distinguishesNoMovementNoCoverageAndMissingTimestamps() {
        assertThat(calculate(START, time(60), List.of(), samples(60, 120), List.of()).unavailableReason()).isEqualTo(NO_MOVING_DATA);
        assertThat(calculate(START, time(60), motion(60, 200), List.of(hr(30, 120)), List.of()).unavailableReason()).isEqualTo(NO_MATCHED_DATA);
        assertThat(calculate(null, time(60), motion(60, 200), samples(60, 120), List.of()).unavailableReason()).isEqualTo(MISSING_TIMESTAMPS);
    }

    @Test
    void doesNotDoubleCountDuplicateMotionIntervals() {
        var interval = new MotionInterval(START, time(30), com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.MOVING, 1.0);
        var result = calculate(START, time(60), List.of(interval, interval), samples(60, 120), List.of());
        assertThat(result.movingSeconds()).isEqualTo(30);
        assertThat(result.estimatedHeartbeats()).isEqualTo(60);
    }

    private static List<MotionInterval> motion(int seconds, double watts) {
        return IntStream.range(0, seconds / 30).mapToObj(i -> new MotionInterval(time(i * 30), time((i + 1) * 30),
                com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.MOVING, watts * 30 / 3600)).toList();
    }

    private static List<HeartRateSample> samples(int seconds, double bpm) {
        return IntStream.rangeClosed(0, seconds / 30).mapToObj(i -> hr(i * 30, bpm)).toList();
    }

    private static HeartRateSample hr(int seconds, double bpm) {
        return new HeartRateSample(time(seconds), bpm);
    }

    private static Instant time(int seconds) {
        return START.plusSeconds(seconds);
    }
}
