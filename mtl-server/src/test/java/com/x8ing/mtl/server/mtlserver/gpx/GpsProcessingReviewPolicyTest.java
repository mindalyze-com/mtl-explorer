package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackObservation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.x8ing.mtl.server.mtlserver.gpx.GpsPipelineReliabilityTest.*;

class GpsProcessingReviewPolicyTest {
    private static List<TrackObservation> points(int count) {
        List<TrackObservation> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(new TrackObservation(i, 0, 47.0, 8.0 + i * .00001,
                500.0, Instant.parse("2030-01-01T00:00:00Z").plusSeconds(i * 10L), null, false));
        return result;
    }

    private static GpsProcessingIssue issue(int index, String action, Stage stage, Reason reason, double magnitude) {
        var finding = new TrackFinding(stage, reason, Severity.WARNING, 0, index, index, 1, magnitude, "metres", "Synthetic action", List.of("Map"));
        return new GpsProcessingIssue(index, index, action, "synthetic", stage == Stage.POSITION ? magnitude : null,
                stage == Stage.ELEVATION ? magnitude : null, null, finding);
    }

    private static boolean review(List<GpsProcessingIssue> issues, List<TrackObservation> points) {
        return GpsProcessingAssessment.reviewFindings(issues, points).stream().anyMatch(f -> f.severity() == Severity.REVIEW);
    }

    @Test
    void smallCorrectionAndCoverageBoundariesRespectActualPointMagnitude() {
        var points = points(1000);
        assertFalse(review(List.of(issue(30, "LARGE_POSITION_CORRECTION", Stage.POSITION, Reason.POSITION_CORRECTION, 30)), points));
        assertFalse(review(List.of(issue(30, "LARGE_POSITION_CORRECTION", Stage.POSITION, Reason.POSITION_CORRECTION, 199.999)), points));
        assertTrue(review(List.of(issue(30, "LARGE_POSITION_CORRECTION", Stage.POSITION, Reason.POSITION_CORRECTION, 200)), points));
        for (int count : new int[]{9, 10}) {
            var issues = new ArrayList<GpsProcessingIssue>();
            for (int i = 0; i < count; i++) issues.add(issue(i, "LARGE_ELEVATION_CORRECTION", Stage.ELEVATION, Reason.ELEVATION_CORRECTION, 50));
            assertEquals(count == 10, review(issues, points));
        }
        var small = new ArrayList<GpsProcessingIssue>();
        for (int i = 0; i < 20; i++) small.add(issue(i, "LARGE_ELEVATION_CORRECTION", Stage.ELEVATION, Reason.ELEVATION_CORRECTION, 49.999));
        assertFalse(review(small, points));
    }

    @Test
    void repairedSpikesRequireExtremeMagnitudeOrSubstantialLoss() {
        var points = points(1000);
        assertFalse(review(List.of(issue(30, "POSITION_REJECTED", Stage.POSITION, Reason.POSITION_JUMP, 999)), points));
        assertTrue(review(List.of(issue(30, "POSITION_REJECTED", Stage.POSITION, Reason.POSITION_JUMP, 1000)), points));
        assertFalse(review(List.of(issue(30, "ELEVATION_REJECTED", Stage.ELEVATION, Reason.ELEVATION_SPIKE, 299)), points));
        assertTrue(review(List.of(issue(30, "ELEVATION_REJECTED", Stage.ELEVATION, Reason.ELEVATION_SPIKE, 300)), points));
        var issues = new ArrayList<GpsProcessingIssue>();
        for (int i = 0; i < 10; i++) issues.add(issue(i, "ELEVATION_REJECTED", Stage.ELEVATION, Reason.ELEVATION_SPIKE, 60));
        assertTrue(review(issues, points));
        var duplicates = new ArrayList<>(issues.subList(0, 5));
        duplicates.addAll(issues.subList(0, 5));
        assertFalse(review(duplicates, points));
    }

    @Test
    void weakFixCountsHorizontalAndVerticalEvidenceOnceAndChecksContinuousTime() {
        var issues = new ArrayList<GpsProcessingIssue>();
        for (int i = 0; i < 5; i++) {
            issues.add(issue(i, "WEAK_SOURCE_FIX", Stage.POSITION, Reason.WEAK_SOURCE_FIX, 0));
            issues.add(issue(i, "WEAK_VERTICAL_FIX", Stage.ELEVATION, Reason.WEAK_SOURCE_FIX, 0));
        }
        assertFalse(review(issues, points(100)));
        assertTrue(review(issues, points(50)));
        issues.clear();
        for (int i = 100; i <= 130; i++) issues.add(issue(i, "WEAK_SOURCE_FIX", Stage.POSITION, Reason.WEAK_SOURCE_FIX, 0));
        assertTrue(review(issues, points(10000)));
        issues.removeIf(i -> i.rawPointIndex() == 115);
        assertFalse(review(issues, points(10000)));
    }

    @Test
    void routineKalmanDenoisingDoesNotBecomeAggregateReview() {
        var input = straight(301, 2, 1);
        for (int i = 0; i < input.size(); i++) {
            input.get(i).setZ(500 + (i % 2) * 2);
            input.get(i).setY(input.get(i).getY() + (i % 2 == 0 ? -6 : 6) / 111320.0);
        }
        var result = read(input);
        result.trackCleaned = GPXStoreService.smoothRecordingSpans(result.trackCleaned, result.recordingBreakBeforePointIndexes,
                new KalmanElevationSmoother(6, 10, 2, .5), result.observationEvidence);
        double rawAscent = java.util.stream.IntStream.range(1, result.trackRAW.getNumPoints())
                .mapToDouble(i -> Math.max(0, result.trackRAW.getCoordinateN(i).getZ() - result.trackRAW.getCoordinateN(i - 1).getZ())).sum();
        double canonicalAscent = java.util.stream.IntStream.range(1, result.trackCleaned.getNumPoints())
                .mapToDouble(i -> Math.max(0, result.trackCleaned.getCoordinateN(i).getZ() - result.trackCleaned.getCoordinateN(i - 1).getZ())).sum();
        assertTrue(rawAscent - canonicalAscent > 100, "Routine two-metre noise exceeds the former aggregate threshold");
        double rawDistance = java.util.stream.IntStream.range(1, result.trackRAW.getNumPoints())
                .mapToDouble(i -> GPXReader.getDistanceBetweenTwoWGS84(result.trackRAW.getCoordinateN(i - 1), result.trackRAW.getCoordinateN(i))).sum();
        double canonicalDistance = java.util.stream.IntStream.range(1, result.trackCleaned.getNumPoints())
                .mapToDouble(i -> GPXReader.getDistanceBetweenTwoWGS84(result.trackCleaned.getCoordinateN(i - 1), result.trackCleaned.getCoordinateN(i))).sum();
        assertTrue(rawDistance - canonicalDistance > 500, "Normal position noise exceeds the former distance threshold");
        var issues = GpsProcessingAssessment.record(result);
        assertFalse(issues.stream().anyMatch(GpsProcessingIssue::aggregateMetricImpact));
        assertFalse(GpsProcessingAssessment.reviewFindings(issues).stream().anyMatch(f -> f.severity() == Severity.REVIEW));
    }

    @Test
    void retainedLargeSmoothingCorrectionsKeepTheirPerPointReview() {
        var result = read(straight(100, 20, 1));
        var coordinates = result.trackCleaned.getCoordinates();
        coordinates[40].setZ(750);
        result.trackCleaned = result.trackCleaned.getFactory().createLineString(coordinates);
        var issues = GpsProcessingAssessment.record(result);
        assertFalse(issues.stream().anyMatch(GpsProcessingIssue::aggregateMetricImpact));
        assertTrue(issues.stream().anyMatch(f -> f.reason().equals("LARGE_ELEVATION_CORRECTION") && f.finding().severity() == Severity.REVIEW));
    }

    @Test
    void retiredAggregateFindingsAreIgnoredAndRealPointCorrectionsRemain() {
        for (String prefix : List.of("canonical-", "cleanup-")) {
            for (String metric : List.of("distance", "ascent", "descent")) {
                boolean elevation = !metric.equals("distance");
                var finding = new TrackFinding(elevation ? Stage.ELEVATION : Stage.POSITION,
                        elevation ? Reason.ELEVATION_CORRECTION : Reason.POSITION_CORRECTION,
                        Severity.REVIEW, 0, null, null, 0, 2000.0, "metres", "Retired aggregate comparison", List.of("Map"));
                var old = new GpsProcessingIssue(null, null,
                        elevation ? "LARGE_ELEVATION_CORRECTION" : "LARGE_POSITION_CORRECTION",
                        prefix + metric + "-impact", elevation ? null : 2000.0, elevation ? 2000.0 : null, null, finding);
                assertTrue(GpsProcessingAssessment.reviewFindings(List.of(old)).isEmpty());
                assertTrue(GpsProcessingAssessment.reviewFindings(List.of(old), points(100)).isEmpty());
                var point = issue(30, "LARGE_ELEVATION_CORRECTION", Stage.ELEVATION, Reason.ELEVATION_CORRECTION, 250);
                var current = GpsProcessingAssessment.reviewFindings(List.of(old, point), points(100));
                assertEquals(1, current.size());
                assertEquals(30, current.getFirst().firstPointIndex());
                assertEquals(Severity.REVIEW, current.getFirst().severity());
            }
        }
    }

    @Test
    void repeatedClockPrecisionDoesNotBecomePositionLossOrFalseSpeed() throws Exception {
        var input = straight(100, 20, 1);
        for (int i = 1; i < input.size(); i += 2) input.get(i).setM(input.get(i - 1).getM());
        var result = read(input);
        assertEquals(input.size(), result.trackCleaned.getNumPoints());
        assertTrue(GpsProcessingAssessment.reviewFindings(GpsProcessingAssessment.record(result)).isEmpty());
        var metrics = metrics(result.trackCleaned);
        assertEquals(com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY, metrics.get(1).getIntervalStatus());
        assertNull(metrics.get(1).getDurationBetweenPointsInSec());
    }
}
