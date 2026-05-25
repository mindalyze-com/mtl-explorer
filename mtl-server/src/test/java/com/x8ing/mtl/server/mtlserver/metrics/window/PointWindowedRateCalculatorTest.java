package com.x8ing.mtl.server.mtlserver.metrics.window;

import com.x8ing.mtl.server.mtlserver.metrics.MetricConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointWindowedRateCalculatorTest {

    private static final CanonicalPointView<SamplePoint> VIEW = PointWindowedRateCalculator.viewOf(
            SamplePoint::durationSec,
            SamplePoint::distanceM,
            SamplePoint::ascentSinceStartM,
            SamplePoint::descentSinceStartM);

    @Test
    void defaultGapPolicyAcceptsRegularFifteenSecondCadence() {
        List<SamplePoint> points = List.of(
                point(0.0, 0.0),
                point(15.0, 150.0),
                point(15.0, 150.0),
                point(15.0, 150.0));

        List<WindowedRateSample> samples = new PointWindowedRateCalculator(
                MetricConstants.DEFAULT_DISPLAY_WINDOW_SEC)
                .compute(points, VIEW);

        assertThat(samples.get(1).speedInKmh()).isNull();
        assertThat(samples.get(2).speedInKmh()).isEqualTo(36.0);
        assertThat(samples.get(3).speedInKmh()).isEqualTo(36.0);
    }

    @Test
    void defaultGapPolicyResetsFifteenSecondCadenceAfterMissedSample() {
        List<SamplePoint> points = List.of(
                point(0.0, 0.0),
                point(15.0, 150.0),
                point(15.0, 150.0),
                point(30.0, 300.0));

        List<WindowedRateSample> samples = new PointWindowedRateCalculator(
                MetricConstants.DEFAULT_DISPLAY_WINDOW_SEC)
                .compute(points, VIEW);

        assertThat(samples.get(2).speedInKmh()).isEqualTo(36.0);
        assertThat(samples.get(3).speedInKmh()).isNull();
    }

    @Test
    void defaultGapPolicyStillResetsDenseTrackAfterDropout() {
        List<SamplePoint> points = new ArrayList<>();
        points.add(point(0.0, 0.0));
        for (int i = 0; i < 30; i++) {
            points.add(point(1.0, 10.0));
        }
        points.add(point(20.0, 200.0));

        List<WindowedRateSample> samples = new PointWindowedRateCalculator(
                MetricConstants.DEFAULT_DISPLAY_WINDOW_SEC)
                .compute(points, VIEW);

        assertThat(samples.get(30).speedInKmh()).isEqualTo(36.0);
        assertThat(samples.get(31).speedInKmh()).isNull();
    }

    @Test
    void explicitMaxGapPolicyKeepsRequestedThreshold() {
        List<SamplePoint> points = List.of(
                point(0.0, 0.0),
                point(15.0, 150.0),
                point(15.0, 150.0));

        List<WindowedRateSample> samples = new PointWindowedRateCalculator(
                MetricConstants.DEFAULT_DISPLAY_WINDOW_SEC,
                TrailingWindow.DEFAULT_MAX_GAP_SEC)
                .compute(points, VIEW);

        assertThat(samples.get(2).speedInKmh()).isNull();
    }

    @Test
    void defaultGapPolicyFallsBackToFloorWhenNoDurationsArePositive() {
        // All durations are zero: no cadence can be measured.
        // The calculator must not throw and must return empty samples.
        List<SamplePoint> points = List.of(
                point(0.0, 0.0),
                point(0.0, 0.0),
                point(0.0, 0.0));

        List<WindowedRateSample> samples = new PointWindowedRateCalculator(
                MetricConstants.DEFAULT_DISPLAY_WINDOW_SEC)
                .compute(points, VIEW);

        assertThat(samples).hasSize(3);
        assertThat(samples).allSatisfy(s -> assertThat(s.speedInKmh()).isNull());
    }

    @Test
    void defaultGapPolicyResetsOnMissedBeatWithJitteredCadence() {
        // Positive durations: [15, 16, 14, 30] sorted as [14, 15, 16, 30].
        // Median = index 2 = 16 s.
        // missedBeatGap = 16 * 1.75 = 28 s
        // effectiveGap = max(10, 28) = 28 s; 30 s step > 28 s, so reset.
        List<SamplePoint> points = List.of(
                point(0.0, 0.0),
                point(15.0, 150.0),
                point(16.0, 160.0),
                point(14.0, 140.0),
                point(30.0, 300.0));  // missed beat

        List<WindowedRateSample> samples = new PointWindowedRateCalculator(
                MetricConstants.DEFAULT_DISPLAY_WINDOW_SEC)
                .compute(points, VIEW);

        assertThat(samples.get(3).speedInKmh()).isEqualTo(36.0); // window active before missed beat
        assertThat(samples.get(4).speedInKmh()).isNull();         // reset on missed beat
    }

    @Test
    void gapPolicyRejectsInvalidValues() {
        assertThatThrownBy(() -> new PointWindowedRateCalculator.GapPolicy(0.0, 1.75, 30.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixedMinGapSec");
        assertThatThrownBy(() -> new PointWindowedRateCalculator.GapPolicy(10.0, -1.0, 30.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missedBeatMultiplier");
        assertThatThrownBy(() -> new PointWindowedRateCalculator.GapPolicy(10.0, 1.75, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxGapSec");
    }

    private static SamplePoint point(double durationSec, double distanceM) {
        return new SamplePoint(durationSec, distanceM, 0.0, 0.0);
    }

    private record SamplePoint(double durationSec,
                               double distanceM,
                               double ascentSinceStartM,
                               double descentSinceStartM) {
    }
}
