package com.x8ing.mtl.server.mtlserver.logic.motion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;

import java.util.ArrayList;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.*;
import static org.junit.jupiter.api.Assertions.*;

class TrackMotionAnalyzerTest {
    private static Coordinate p(double x, double t) {
        return new CoordinateXYZM(x / 111320, 0, 100, t);
    }

    private static Analysis analyze(List<Coordinate> points) {
        return TrackMotionAnalyzer.analyze(points, List.of());
    }

    private static List<Coordinate> still(int duration, int step) {
        List<Coordinate> points = new ArrayList<>();
        for (int t = 0; t <= duration; t += step) points.add(p(0, t));
        return points;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10, 15, 30, 60})
    void elapsedTimeNotSampleCountDeterminesBreak(int step) {
        var result = analyze(still(300, step));
        assertEquals(Status.COMPLETE, result.status());
        assertEquals(300, result.stoppedSeconds());
        assertEquals(1, result.breaks().size());
        assertEquals(0, result.breaks().getFirst().startTimeS());
        assertEquals(300, result.breaks().getFirst().endTimeS());
        assertTrue(result.breaks().getFirst().confirmedAtS() >= 60);
    }

    @Test
    void shortStopTimeDoesNotCreateVisibleBreak() {
        var result = analyze(still(30, 5));
        assertEquals(30, result.stoppedSeconds());
        assertEquals(30, result.shortStoppedSeconds());
        assertTrue(result.breaks().isEmpty());
    }

    @Test
    void twoEndpointsAloneDoNotProveAStop() {
        var result = analyze(List.of(p(0, 0), p(0, 60)));
        assertEquals(60, result.unknownSeconds());
        assertEquals(Reason.INSUFFICIENT_OBSERVATIONS, result.intervals().getLast().reason());
        assertTrue(result.breaks().isEmpty());
    }

    @Test
    void gapIsUnknownEvenWithIdenticalEndpoints() {
        var result = analyze(List.of(p(0, 0), p(0, 600), p(0, 1200)));
        assertEquals(1200, result.unknownSeconds());
        assertEquals(0, result.stoppedSeconds());
        assertEquals(Reason.SPARSE_SUPPORT, result.intervals().getLast().reason());
    }

    @Test
    void sourceBreakSplitsStationarySpansAndCensorsBoundaries() {
        var result = TrackMotionAnalyzer.analyze(still(180, 10), List.of(9));
        assertEquals(2, result.breaks().size());
        assertEquals(10, result.unknownSeconds());
        assertEquals(170, result.stoppedSeconds());
        assertEquals(BoundaryStatus.RECORDING_EDGE, result.breaks().getFirst().endBoundary().status());
        assertEquals(BoundaryStatus.RECORDING_EDGE, result.breaks().getLast().startBoundary().status());
    }

    @Test
    void refinementRetainsObservationBracketsAndDelayedConfirmation() {
        List<Coordinate> points = new ArrayList<>();
        points.add(p(-10, 0));
        for (int t = 10; t <= 130; t += 10) points.add(p(0, t));
        points.add(p(10, 140));
        var range = analyze(points).breaks().getFirst();
        assertEquals(10, range.startTimeS());
        assertEquals(130, range.endTimeS());
        assertEquals(70, range.confirmedAtS());
        assertEquals(new Boundary(0, 10, BoundaryStatus.BRACKETED), range.startBoundary());
        assertEquals(new Boundary(130, 140, BoundaryStatus.BRACKETED), range.endBoundary());
    }

    @Test
    void retrospectivePassIncludesSupportedAmbiguousEdges() {
        List<Coordinate> points = new ArrayList<>();
        points.add(p(-1, 0));
        points.add(p(0, 10)); // 0.36 km/h, below movement threshold but above core entry.
        for (int t = 20; t <= 130; t += 10) points.add(p(0, t));
        points.add(p(1, 140));
        var range = analyze(points).breaks().getFirst();
        assertEquals(0, range.startTimeS());
        assertEquals(140, range.endTimeS());
        assertEquals(BoundaryStatus.TRACK_EDGE, range.startBoundary().status());
    }

    @Test
    void sustainedVerySlowProgressRemainsUnknown() {
        List<Coordinate> points = new ArrayList<>();
        for (int t = 0; t <= 600; t += 10) points.add(p(t * .05, t));
        var result = analyze(points);
        assertTrue(result.breaks().isEmpty());
        assertEquals(600, result.unknownSeconds());
    }

    @Test
    void stillCoreSurvivesVerySlowApproachAndDeparture() {
        List<Coordinate> points = new ArrayList<>();
        for (int t = 0; t <= 600; t += 10) points.add(p((t - 600) * .05, t));
        for (int t = 610; t <= 900; t += 10) points.add(p(0, t));
        for (int t = 910; t <= 1500; t += 10) points.add(p((t - 900) * .05, t));
        var result = analyze(points);
        assertEquals(300, result.stoppedSeconds());
        assertEquals(1200, result.unknownSeconds());
        assertEquals(600, result.breaks().getFirst().startTimeS());
        assertEquals(900, result.breaks().getFirst().endTimeS());
    }

    @ParameterizedTest
    @ValueSource(ints = {59, 60, 179, 180, 599, 600})
    void durationCategoriesApplyAfterFullRangeRefinement(int duration) {
        var result = analyze(still(duration, 1));
        assertEquals(duration, result.stoppedSeconds());
        if (duration < 60) assertTrue(result.breaks().isEmpty());
        else {
            var range = TrackStopDetector.eventRanges(result).getFirst();
            assertEquals(duration >= 600 ? TrackStopDetector.StopCategory.LONG_BREAK
                    : duration >= 180 ? TrackStopDetector.StopCategory.BREAK : TrackStopDetector.StopCategory.SHORT_STOP, range.category());
        }
    }

    @Test
    void verticalTravelVetoesStop() {
        List<Coordinate> points = new ArrayList<>();
        for (int t = 0; t <= 120; t += 10) points.add(new CoordinateXYZM(0, 0, 100 + t, t));
        assertEquals(120, analyze(points).movingSeconds());
    }

    @Test
    void invalidTimeDoesNotInventElapsedSecondsOrJoinSpans() {
        for (double badTime : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -10}) {
            var result = analyze(List.of(p(0, 0), p(0, badTime), p(0, 20)));
            assertNotEquals(Status.COMPLETE, result.status());
            assertTrue(result.breaks().isEmpty());
            assertTrue(Double.isFinite(result.unknownSeconds()));
        }
    }

    @Test
    void unavailableIsDifferentFromZeroMotion() {
        assertEquals(Status.UNAVAILABLE, analyze(List.of()).status());
        assertEquals(Status.UNAVAILABLE, analyze(List.of(p(0, Double.NaN))).status());
    }

    @Test
    void fractionalSecondsArePreservedAtPersistencePrecision() {
        var result = analyze(List.of(p(0, .125), p(0, 30.375), p(0, 60.625)));
        assertEquals(60.5, result.stoppedSeconds());
    }

    @Test
    void duplicateTimestampsDoNotSatisfyMinimumObservations() {
        var result = analyze(List.of(p(0, 0), p(0, 0), p(0, 60)));
        assertEquals(0, result.stoppedSeconds());
        assertEquals(Status.COMPLETE, result.status());
    }

    @Test
    void duplicatesPreserveSupportedMotionTotalsAndPrecisionJitterDoesNotDoubleCountTime() {
        var duplicate = analyze(List.of(p(0, 0), p(10, 10), p(10, 10), p(20, 20)));
        assertEquals(Status.COMPLETE, duplicate.status());
        assertEquals(20, duplicate.movingSeconds());
        assertEquals(0, duplicate.intervals().get(2).durationS());
        var input = List.of(p(0, 0), p(10, 10), p(10, 9.5), p(20, 20));
        var jitter = analyze(input);
        assertEquals(Status.COMPLETE, jitter.status());
        assertEquals(20, jitter.movingSeconds());
        assertEquals(9.5, input.get(2).getM(), "Original observations must remain unchanged");
    }

    @Test
    void antimeridianCoordinatesUseWrappedCenter() {
        var points = List.<Coordinate>of(new CoordinateXYZM(179.999999, 0, 10, 0),
                new CoordinateXYZM(-179.999999, 0, 10, 30), new CoordinateXYZM(179.999999, 0, 10, 60));
        var result = analyze(points);
        assertEquals(60, result.stoppedSeconds());
        assertTrue(Math.abs(result.breaks().getFirst().centerLng()) > 179);
    }

    @Test
    void classificationNeverChangesCoordinatesOrSourceOrder() {
        var points = still(300, 5);
        var before = points.stream().map(Coordinate::copy).toList();
        var first = analyze(points);
        assertEquals(first, analyze(points));
        for (int i = 0; i < points.size(); i++) {
            assertEquals(before.get(i).getX(), points.get(i).getX());
            assertEquals(before.get(i).getY(), points.get(i).getY());
            assertEquals(before.get(i).getZ(), points.get(i).getZ());
            assertEquals(before.get(i).getM(), points.get(i).getM());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {3600, 86400})
    void fullDayIsBoundedAndKeepsEveryInterval(int seconds) {
        var result = assertTimeout(java.time.Duration.ofSeconds(10), () -> analyze(still(seconds, 1)));
        assertEquals(seconds + 1, result.intervals().size());
        assertEquals(seconds, result.stoppedSeconds());
    }
}
