package com.x8ing.mtl.server.mtlserver.logic.motion;

import com.x8ing.mtl.server.mtlserver.gpx.BreakStopTreatment;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression scenarios from synthetic ground truth on accepted denoised observations.
 */
class TrackStopDetectorTest {
    private static Coordinate p(double x, double y, double t) {
        return new CoordinateXYZM(8 + x / (111320 * Math.cos(Math.toRadians(47))), 47 + y / 111320, 500, t);
    }

    private static List<Coordinate> still(int duration, int step) {
        List<Coordinate> out = new ArrayList<>();
        for (int t = 0; t <= duration; t += step) out.add(p(0, 0, t));
        return out;
    }

    private static List<Coordinate> excursion() {
        List<Coordinate> out = still(120, 5);
        for (int i = 1; i <= 5; i++) out.add(p(40 * i, 0, 120 + i * 10));
        for (int i = 1; i <= 5; i++) out.add(p(40 * (5 - i), 0, 170 + i * 10));
        for (int t = 225; t <= 340; t += 5) out.add(p(0, 0, t));
        return out;
    }

    private static List<Coordinate> circle(int step) {
        List<Coordinate> out = new ArrayList<>();
        for (int t = 0; t <= 600; t += step) out.add(p(20 * Math.cos(2 * Math.PI * t / 120), 20 * Math.sin(2 * Math.PI * t / 120), t));
        return out;
    }

    private static List<Coordinate> pauseWithApproach(double speed) {
        List<Coordinate> out = new ArrayList<>();
        for (int t = 0; t < 60; t += 5) out.add(p((t - 60) * speed, 0, t));
        for (int t = 60; t <= 120; t += 5) out.add(p(0, 0, t));
        for (int t = 125; t <= 180; t += 5) out.add(p((t - 120) * speed, 0, t));
        return out;
    }

    @Test
    void detectsDenseFiveMinutePause() {
        var ranges = TrackStopDetector.detectRawStopRangesInCoordinates(still(300, 5));
        assertEquals(1, ranges.size());
        assertEquals(300, ranges.getFirst().durationS(), 0.001);
    }

    @Test
    void detectsSameFiveMinutePauseAtThirtySecondSampling() {
        var ranges = TrackStopDetector.detectRawStopRangesInCoordinates(still(300, 30));
        assertEquals(1, ranges.size(), "Eleven exact stationary observations span a five-minute pause");
        assertEquals(300, ranges.getFirst().durationS(), 0.001);
    }

    @Test
    void keepsTwoPausesSeparateAcrossRecordedRun() {
        var ranges = TrackStopDetector.detectRawStopRangesInCoordinates(excursion());
        assertEquals(2, ranges.size(), "Two 120-second pauses separated by 100 seconds of movement");
        assertEquals(240, TrackStopDetector.summarizeStopRanges(ranges).totalStoppedSec, 0.001);
    }

    @Test
    void preservesRecordedExcursionGeometry() {
        var result = BreakStopTreatment.apply(excursion());
        assertEquals(0, result.spikesRemoved(), "The excursion consists of supported 40-metre steps");
        assertTrue(result.cleanedCoordinates().stream().anyMatch(c -> c.getX() > p(199, 0, 0).getX()),
                "The observed 200-metre turnaround must survive stop treatment");
    }

    @Test
    void doesNotLabelContinuousDenseWalkingAsStop() {
        assertTrue(TrackStopDetector.detectRawStopRangesInCoordinates(circle(5)).isEmpty());
    }

    @Test
    void doesNotLabelSameContinuousWalkingAsStopAtThirtySecondSampling() {
        assertTrue(TrackStopDetector.detectRawStopRangesInCoordinates(circle(30)).isEmpty(),
                "Every sample moves about 28 metres; the trajectory never stops");
    }

    @Test
    void locatesPauseBoundariesDuringOrdinaryWalking() {
        var ranges = TrackStopDetector.detectRawStopRangesInCoordinates(pauseWithApproach(1));
        assertEquals(1, ranges.size());
        assertEquals(60, ranges.getFirst().startTimeS(), 0.001);
        assertEquals(120, ranges.getFirst().endTimeS(), 0.001);
    }

    @Test
    void locatesPauseBoundariesDuringSlowMovementWithinOneSample() {
        var ranges = TrackStopDetector.detectRawStopRangesInCoordinates(pauseWithApproach(0.2));
        assertEquals(1, ranges.size());
        assertEquals(60, ranges.getFirst().startTimeS(), 5, "Allow one five-second sample of boundary uncertainty");
        assertEquals(120, ranges.getFirst().endTimeS(), 5);
    }
}
