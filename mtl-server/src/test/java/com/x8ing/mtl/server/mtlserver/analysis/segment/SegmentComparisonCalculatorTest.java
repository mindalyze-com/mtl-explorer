package com.x8ing.mtl.server.mtlserver.analysis.segment;

import org.junit.jupiter.api.Test;

import static com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentAnalysisFixtures.core;
import static org.junit.jupiter.api.Assertions.*;

class SegmentComparisonCalculatorTest {
    @Test
    void finishDifferenceIsSeparateFromEqualDistanceDifference() {
        var result = SegmentComparisonCalculator.compare(core(95, 1000, new double[][]{{0, 0}, {1000, 95}}),
                core(100, 1100, new double[][]{{0, 0}, {1100, 100}}));
        assertEquals(-5d, result.finishDifferenceSeconds());
        assertEquals(95 - 100d * 1000 / 1100, result.points().getLast().differenceSeconds(), 1e-10);
        assertEquals(1000d, result.supportedDistanceMeters());
    }

    @Test
    void neverExtrapolatesPastReferenceCoverage() {
        var attempt = core(120, 1200, new double[][]{{0, 0}, {1100, 110}, {1200, 120}});
        var reference = core(100, 1000, new double[][]{{0, 0}, {1000, 100}});
        var result = SegmentComparisonCalculator.compare(attempt, reference);
        assertTrue(result.points().stream().allMatch(p -> p.distanceMeters() <= 1000));
        assertNull(SegmentComparisonCalculator.timeAt(reference.geometry(), 1100, false));
    }

    @Test
    void fractionalSharedCoverageIncludesStoppedTimeWithoutFinishExtrapolation() {
        var paused = core(120, 1000.8, new double[][]{{0, 0}, {500, 50}, {500, 80}, {1000.8, 120}});
        var regular = core(100, 1000, new double[][]{{0, 0}, {1000, 100}});
        var result = SegmentComparisonCalculator.compare(paused, regular);
        assertEquals(1000d, result.supportedDistanceMeters());
        assertTrue(result.points().stream().allMatch(p -> p.distanceMeters() <= 1000));
        assertEquals(1000d, result.points().getLast().distanceMeters());
        double supportedDifference = 80 + 500d / 500.8 * 40 - 100;
        assertEquals(supportedDifference, result.points().getLast().differenceSeconds(), 1e-10);
        assertEquals(20d, result.finishDifferenceSeconds());
        assertNotEquals(result.finishDifferenceSeconds(), result.points().getLast().differenceSeconds());
        assertNull(SegmentComparisonCalculator.timeAt(regular.geometry(), 1000.8, false));
        var reversed = SegmentComparisonCalculator.compare(regular, paused);
        assertEquals(-supportedDifference, reversed.points().getLast().differenceSeconds(), 1e-10);
        assertEquals(-20d, reversed.finishDifferenceSeconds());
    }

    @Test
    void exactDistanceUsesArrivalAndFollowingInterpolationIncludesStoppedTime() {
        var paused = core(100, 100, new double[][]{{0, 0}, {50, 50}, {50, 80}, {100, 100}});
        assertEquals(50d, SegmentComparisonCalculator.timeAt(paused.geometry(), 50, false));
        assertEquals(80d, SegmentComparisonCalculator.timeAt(paused.geometry(), 50, true));
        assertEquals(90d, SegmentComparisonCalculator.timeAt(paused.geometry(), 75, false));
        var result = SegmentComparisonCalculator.compare(paused, core(100, 100, new double[][]{{0, 0}, {100, 100}}));
        assertEquals(0d, result.points().stream().filter(p -> p.distanceMeters() == 50 && p.positionMeaning().equals("ARRIVAL")).findFirst().orElseThrow().differenceSeconds());
        assertEquals(30d, result.points().stream().filter(p -> p.positionMeaning().equals("DEPARTURE_LIMIT")).findFirst().orElseThrow().differenceSeconds());
    }

    @Test
    void recordingGapsBreakCurvesEvenWhenBothEndpointsHaveTimes() {
        var broken = core(100, 100, new double[][]{{0, 0}, {50, 50}, {100, 100, 1}});
        assertNull(SegmentComparisonCalculator.timeAt(broken.geometry(), 75, false));
        var result = SegmentComparisonCalculator.compare(broken, core(100, 100, new double[][]{{0, 0}, {100, 100}}));
        assertTrue(result.points().getLast().breakBefore());
    }

    @Test
    void ghostShowsWhereTheAttemptWasAtTheReferenceArrivalTime() {
        // The attempt covers 1000 m in 80 s; the reference needs 100 s.
        var result = SegmentComparisonCalculator.compare(core(80, 1000, new double[][]{{0, 0}, {1000, 80}}),
                core(100, 1000, new double[][]{{0, 0}, {500, 50}, {1000, 100}}));
        var halfway = ghost(result, 500, "ARRIVAL");
        assertEquals(50d, halfway.referenceElapsedSeconds(), 1e-10);
        assertEquals(625d, halfway.distanceMeters(), 1e-10);
        assertEquals(125d, halfway.leadMeters(), 1e-10);
        assertEquals(SegmentGhostPoint.IN_PROGRESS, halfway.state());
        // The attempt's own finish time becomes a reference breakpoint.
        assertEquals(200d, ghost(result, 800, "ARRIVAL").leadMeters(), 1e-10);
        var end = ghost(result, 1000, "ARRIVAL");
        assertEquals(SegmentGhostPoint.FINISHED, end.state());
        assertEquals(0d, end.leadMeters(), 1e-10);
    }

    @Test
    void slowerAttemptHasNegativeLeadAndGhostsCoverTheWholeReference() {
        var result = SegmentComparisonCalculator.compare(core(100, 800, new double[][]{{0, 0}, {800, 100}}),
                core(50, 1000, new double[][]{{0, 0}, {500, 25}, {1000, 50}}));
        assertEquals(-300d, ghost(result, 500, "ARRIVAL").leadMeters(), 1e-10);
        assertEquals(1000d, result.ghostPoints().getLast().referenceDistanceMeters());
        assertTrue(result.points().stream().allMatch(p -> p.distanceMeters() <= 800));
    }

    @Test
    void referenceStopAddsDepartureGhostWhileTheAttemptKeepsMoving() {
        var reference = core(100, 100, new double[][]{{0, 0}, {50, 50}, {50, 80}, {100, 100}});
        var result = SegmentComparisonCalculator.compare(core(100, 100, new double[][]{{0, 0}, {100, 100}}), reference);
        assertEquals(50d, ghost(result, 50, "ARRIVAL").distanceMeters(), 1e-10);
        var departure = ghost(result, 50, "DEPARTURE_LIMIT");
        assertEquals(80d, departure.distanceMeters(), 1e-10);
        assertTrue(departure.breakBefore());
    }

    @Test
    void attemptRecordingGapBreaksTheGhostCurve() {
        var result = SegmentComparisonCalculator.compare(core(100, 100, new double[][]{{0, 0}, {50, 50}, {100, 100, 1}}),
                core(100, 100, new double[][]{{0, 0}, {100, 100}}));
        assertEquals(SegmentGhostPoint.IN_PROGRESS, ghost(result, 50, "ARRIVAL").state());
        assertFalse(ghost(result, 50, "ARRIVAL").breakBefore());
        assertTrue(ghost(result, 100, "ARRIVAL").breakBefore());
    }

    @Test
    void endpointFinishDifferenceRemainsAvailableWhenPathDistanceIsUnknown() {
        var original = core(120, 100, new double[][]{{0, 0}, {100, 120}});
        var summary = new SegmentAttemptSummary(original.identity(), null, 120d, null, null, null, null, null, java.util.Map.of());
        var incomplete = new SegmentCoreAnalysis(original.identity(), summary, original.points(), original.geometry(), 2);
        var result = SegmentComparisonCalculator.compare(incomplete, core(100, 100, new double[][]{{0, 0}, {100, 100}}));
        assertEquals(20d, result.finishDifferenceSeconds());
        assertNull(result.supportedDistanceMeters());
        assertTrue(result.points().isEmpty());
        assertTrue(result.ghostPoints().isEmpty());
        assertEquals("DISTANCE_UNAVAILABLE", result.unavailableReason());
    }

    private static SegmentGhostPoint ghost(SegmentComparison result, double referenceDistance, String meaning) {
        return result.ghostPoints().stream()
                .filter(p -> p.referenceDistanceMeters() == referenceDistance && p.positionMeaning().equals(meaning))
                .findFirst().orElseThrow();
    }
}
