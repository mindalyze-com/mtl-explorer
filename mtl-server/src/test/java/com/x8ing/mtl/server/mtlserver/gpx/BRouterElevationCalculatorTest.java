package com.x8ing.mtl.server.mtlserver.gpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Synthetic profiles only; no recorded or user-supplied GPX data.
 */
class BRouterElevationCalculatorTest {
    private static final double EPSILON = 1e-8;

    @ParameterizedTest
    @ValueSource(doubles = {5, 10})
    void gentleDescentAccumulatesSubMetreSteps(double buffer) {
        double[] heights = new double[401];
        Arrays.setAll(heights, i -> 300 - i * .25);
        assertTotals(heights, buffer, 0, 100);
    }

    @ParameterizedTest
    @ValueSource(doubles = {5, 10})
    void gentleAscentAccumulatesSubMetreSteps(double buffer) {
        double[] heights = new double[401];
        Arrays.setAll(heights, i -> 300 + i * .25);
        assertTotals(heights, buffer, 100, 0);
    }

    @Test
    void smallDipsDoNotInflateAContinuousClimb() {
        assertTotals(new double[]{100, 104, 102, 108}, 5, 8, 0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {5, 10})
    void repeatedSmallWigglesDoNotAccumulate(double buffer) {
        double[] heights = new double[1001];
        Arrays.setAll(heights, i -> i % 2 == 0 ? 100 : 99);
        assertTotals(heights, buffer, 0, 0);
    }

    @Test
    void bothUpstreamBufferSizesHaveKnownHillTotals() {
        double[] heights = {100, 120, 100, 120, 100};
        assertTotals(heights, 5, 35, 35);
        assertTotals(heights, 10, 30, 30);
    }

    @Test
    void endpointsAndFractionalMetresAreNotDiscardedOrTruncated() {
        assertTotals(new double[]{100, 99.75}, 10, 0, .25);
        assertTotals(new double[]{100, 100.75}, 10, .75, 0);
        assertTotals(new double[]{100, 120, 110.5}, 10, 20, 9.5);
    }

    @Test
    void missingAndInfiniteHeightsBreakTheProfileInsteadOfCreatingBridges() {
        assertTotals(new double[]{100, 95, Double.NaN, 200, 210,
                Double.POSITIVE_INFINITY, 900, 880}, 10, 10, 25);
    }

    @Test
    void emptySingleAndEntirelyMissingProfilesHaveNoGain() {
        for (double[] heights : new double[][]{{}, {100}, {Double.NaN, Double.NaN}}) {
            assertTotals(heights, 10, 0, 0);
        }
    }

    @Test
    void rejectsInvalidBuffers() {
        for (double buffer : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> BRouterElevationCalculator.calculate(new double[]{100, 101}, buffer));
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {5, 10})
    void reversalBalanceAndPointDensityHoldAcrossSyntheticTerrain(double buffer) {
        Random random = new Random(20260913);
        for (int trial = 0; trial < 200; trial++) {
            double[] heights = new double[101];
            heights[0] = 100;
            for (int i = 1; i < heights.length; i++) heights[i] = heights[i - 1] + random.nextInt(-40, 41) * .25;
            double[] original = heights.clone();
            var totals = BRouterElevationCalculator.calculate(heights, buffer);
            double[] reversed = new double[heights.length];
            Arrays.setAll(reversed, i -> heights[heights.length - 1 - i]);
            var reverseTotals = BRouterElevationCalculator.calculate(reversed, buffer);
            assertEquals(totals.ascentM(), reverseTotals.descentM(), EPSILON);
            assertEquals(totals.descentM(), reverseTotals.ascentM(), EPSILON);
            assertEquals(heights[heights.length - 1] - heights[0], totals.ascentM() - totals.descentM(), EPSILON);
            double[] dense = new double[heights.length * 2 - 1];
            Arrays.setAll(dense, i -> i % 2 == 0 ? heights[i / 2] : (heights[i / 2] + heights[i / 2 + 1]) / 2);
            assertEquals(totals, BRouterElevationCalculator.calculate(dense, buffer));
            assertArrayEquals(original, heights);
        }
    }

    private static void assertTotals(double[] heights, double buffer, double ascent, double descent) {
        var actual = BRouterElevationCalculator.calculate(heights, buffer);
        assertEquals(ascent, actual.ascentM(), EPSILON);
        assertEquals(descent, actual.descentM(), EPSILON);
    }
}
