package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KalmanStationaryObservationTest {
    @ParameterizedTest
    @ValueSource(ints = {30, 120})
    void realImportAndSmoothingRetainStationaryTiming(int pauseSeconds) {
        var loaded = GpsPipelineReliabilityTest.read(recording(pauseSeconds, false));
        var canonical = GpsPipelineReliabilityTest.smooth(loaded);
        var motion = TrackMotionAnalyzer.analyze(Arrays.asList(canonical.getCoordinates()),
                loaded.recordingBreakBeforePointIndexes);

        assertEquals(TrackMotionAnalyzer.Status.COMPLETE, motion.status());
        assertEquals(100, motion.movingSeconds());
        assertEquals(pauseSeconds, motion.stoppedSeconds());
        assertEquals(0, motion.unknownSeconds());
        assertEquals(pauseSeconds < 60 ? pauseSeconds : 0, motion.shortStoppedSeconds());
        assertEquals(pauseSeconds < 60 ? 0 : 1, motion.breaks().size());
        assertEquals(loaded.trackCleaned.getNumPoints(), canonical.getNumPoints());
        for (int i = 0; i < canonical.getNumPoints(); i++) {
            assertEquals(loaded.trackCleaned.getCoordinateN(i).getM(), canonical.getCoordinateN(i).getM());
        }
        for (int i = 5; i <= 5 + pauseSeconds / 10; i++) {
            assertTrue(loaded.trackCleaned.getCoordinateN(i).equals3D(canonical.getCoordinateN(i)),
                    "Smoothing must not invent displacement within repeated accepted positions");
        }
    }

    @Test
    void missingAltitudeStaysMissingWhileTheSupportedPauseSurvives() {
        var loaded = GpsPipelineReliabilityTest.read(recording(30, true));
        var canonical = GpsPipelineReliabilityTest.smooth(loaded);
        var motion = TrackMotionAnalyzer.analyze(Arrays.asList(canonical.getCoordinates()), List.of());
        assertEquals(30, motion.stoppedSeconds());
        assertEquals(100, motion.movingSeconds());
        assertTrue(Arrays.stream(canonical.getCoordinates()).allMatch(c -> Double.isNaN(c.getZ())));
    }

    @Test
    void repeatedPositionsAcrossRecordingBreaksDoNotSupplyAStationaryCore() {
        var input = GpsPipelineReliabilityTest.line(List.of(
                GpsPipelineReliabilityTest.c(0, 0, 500, 0),
                GpsPipelineReliabilityTest.c(0, 0, 500, 10),
                GpsPipelineReliabilityTest.c(0, 0, 500, 20),
                GpsPipelineReliabilityTest.c(0, 0, 500, 30)));
        var canonical = GPXStoreService.smoothRecordingSpans(input, List.of(2),
                new KalmanElevationSmoother(6, 10, 2, .5));
        var motion = TrackMotionAnalyzer.analyze(Arrays.asList(canonical.getCoordinates()), List.of(2));
        assertEquals(0, motion.stoppedSeconds());
        assertEquals(30, motion.unknownSeconds());
        assertTrue(motion.breaks().isEmpty());
    }

    @Test
    void sparseRepeatedPositionsRemainUnsupported() {
        var input = GpsPipelineReliabilityTest.line(List.of(
                GpsPipelineReliabilityTest.c(0, 0, 500, 0),
                GpsPipelineReliabilityTest.c(0, 0, 500, 90),
                GpsPipelineReliabilityTest.c(0, 0, 500, 180)));
        var canonical = new KalmanElevationSmoother(6, 10, 2, .5).denoise(input);
        var motion = TrackMotionAnalyzer.analyze(Arrays.asList(canonical.getCoordinates()), List.of());
        assertEquals(0, motion.stoppedSeconds());
        assertEquals(180, motion.unknownSeconds());
    }

    private static List<Coordinate> recording(int pauseSeconds, boolean missingAltitude) {
        List<Coordinate> points = new ArrayList<>();
        for (int time = 0; time <= 100 + pauseSeconds; time += 10) {
            int step = time <= 50 ? time / 10 : time <= 50 + pauseSeconds ? 5 : (time - pauseSeconds) / 10;
            points.add(new CoordinateXYZM(7.45 + step * .0002, 46.953,
                    missingAltitude ? Double.NaN : 555 + step, 1_787_040_000 + time));
        }
        return points;
    }
}
