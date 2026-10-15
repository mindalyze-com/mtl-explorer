package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosticEngine;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackProcessingDiagnosticEngine;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.gpx.GpsPipelineReliabilityTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RetainedPositionContinuityTest {
    @Test
    void abruptRelocationIsRetainedButCannotContributeDistanceOrSpeed() throws Exception {
        var points = straight(49, 60, 15);
        for (int i = 24; i < points.size(); i++) points.get(i).setX(points.get(i).getX() + 1000 / 76000.0);
        points.forEach(point -> point.setZ(Double.NaN));
        var loaded = read(points);
        assertEquals(49, loaded.trackRAW.getNumPoints());
        assertEquals(49, loaded.trackCleaned.getNumPoints());
        assertEquals(List.of(24), loaded.recordingBreakBeforePointIndexes);
        loaded.trackCleaned = smooth(loaded);
        var issues = GpsProcessingAssessment.record(loaded);
        var jump = issues.stream().filter(issue -> issue.reason().equals("POSITION_DISCONTINUITY")).findFirst().orElseThrow();
        assertEquals(23, jump.finding().firstPointIndex());
        assertEquals(24, jump.finding().lastPointIndex());
        assertEquals(15, jump.durationSeconds());
        assertTrue(jump.horizontalMetres() > 1000);
        assertTrue(jump.finding().message().contains("separate spans"));
        var original = new TrackDiagnosticEngine().diagnose(GpxDocument.parse("<gpx><trk>" + segment(points) + "</trk></gpx>"));
        var findings = new ArrayList<>(original.findings());
        findings.addAll(GpsProcessingAssessment.reviewFindings(issues));
        assertTrue(TrackProcessingDiagnosticEngine.withFindings(original, findings).needsReview());
        var metrics = metrics(loaded.trackCleaned, loaded.recordingBreakBeforePointIndexes);
        assertEquals(GpsTrackDataPoint.IntervalStatus.UNKNOWN, metrics.get(24).getIntervalStatus());
        assertNull(metrics.get(24).getDistanceInMeterBetweenPoints());
        assertTrue(metrics.getLast().getDistanceInMeterSinceStart() < 3000);
        assertTrue(metrics.stream().allMatch(point -> point.getPointAltitude() == null));
        assertTrue(issues.stream().noneMatch(issue -> issue.reason().equals("LARGE_POSITION_CORRECTION")));
    }

    @Test
    void sustainedFastTravelIsNotAContextualDiscontinuity() {
        for (double speed : List.of(50., 100., 250.)) {
            var loaded = read(straight(40, speed * 15, 15));
            assertEquals(List.of(), loaded.recordingBreakBeforePointIndexes, "speed=" + speed);
            assertTrue(GpsProcessingAssessment.record(loaded).stream().noneMatch(issue -> issue.reason().equals("POSITION_DISCONTINUITY")));
        }
    }

    @Test
    void separateSourceSectionsAndMultipleRelocationsKeepTheirIdentities() {
        var first = straight(60, 30, 15);
        for (int i = 20; i < first.size(); i++) first.get(i).setX(first.get(i).getX() + 1000 / 76000.0);
        for (int i = 40; i < first.size(); i++) first.get(i).setX(first.get(i).getX() + 1000 / 76000.0);
        var second = straight(40, 30, 15);
        second.forEach(point -> point.setM(point.getM() + 3600));
        var loaded = read("<trk>" + segment(first) + segment(second) + "</trk>").getFirst();
        assertEquals(List.of(20, 40, 60), loaded.recordingBreakBeforePointIndexes);
        assertEquals(List.of(20, 40, 60), loaded.rawRecordingBreakBeforePointIndexes);
        var jumps = GpsProcessingAssessment.record(loaded).stream().filter(issue -> issue.reason().equals("POSITION_DISCONTINUITY")).toList();
        assertEquals(List.of(20, 40), jumps.stream().map(GpsProcessingIssue::rawPointIndex).toList());
        assertTrue(jumps.stream().allMatch(issue -> issue.finding().sectionIndex() == 0));
        assertEquals(1, loaded.observationEvidence.getLast().sourceRecordingSection());
    }

    @Test
    void extremeEndpointLeapsAreDisconnectedWithoutLosingTheIsolatedPosition() throws Exception {
        for (int endpoint : List.of(0, 7)) {
            var points = straight(8, 30, 15);
            points.get(endpoint).setX(points.get(endpoint).getX() + (endpoint == 0 ? -3000 : 3000) / 76000.0);
            var loaded = read(points);
            int boundary = endpoint == 0 ? 1 : 7;
            assertEquals(8, loaded.trackCleaned.getNumPoints());
            assertEquals(List.of(boundary), loaded.recordingBreakBeforePointIndexes);
            loaded.trackCleaned = smooth(loaded);
            var metrics = metrics(loaded.trackCleaned, loaded.recordingBreakBeforePointIndexes);
            assertEquals(GpsTrackDataPoint.IntervalStatus.UNKNOWN, metrics.get(boundary).getIntervalStatus());
            assertNull(metrics.get(boundary).getDistanceInMeterBetweenPoints());
            assertTrue(metrics.getLast().getDistanceInMeterSinceStart() < 250);
            assertTrue(GpsProcessingAssessment.reviewFindings(GpsProcessingAssessment.record(loaded)).stream()
                    .anyMatch(finding -> finding.reason() == TrackFinding.Reason.POSITION_JUMP));
        }
    }

    @Test
    void shortRecordingsWithoutEnoughContextAreNotAutomaticallyDisconnected() {
        var points = straight(3, 30, 15);
        points.getLast().setX(points.getLast().getX() + 3000 / 76000.0);
        var loaded = read(points);
        assertEquals(List.of(), loaded.recordingBreakBeforePointIndexes);
    }

    @Test
    void cleanTurnsStopsAndReversalsDoNotAddDiscontinuitiesAcrossCadences() {
        for (double dt : List.of(1., 5., 15., 30.))
            for (double speed : List.of(1.4, 5., 10., 60 / 3.6, 25.))
                for (String motion : List.of("turn", "reverse", "stop")) {
                    var points = new ArrayList<Coordinate>();
                    double step = speed * dt;
                    for (int i = 0; i < 50; i++) {
                        double x = Math.min(i, 24) * step, y = 0;
                        if (i > 24 && motion.equals("turn")) y = (i - 24) * step;
                        if (i > 24 && motion.equals("reverse")) x -= (i - 24) * step;
                        points.add(c(x, y, Double.NaN, i * dt));
                    }
                    var loaded = read(points);
                    assertEquals(List.of(), loaded.recordingBreakBeforePointIndexes, motion + " dt=" + dt + " speed=" + speed);
                    loaded.trackCleaned = smooth(loaded);
                    var issues = GpsProcessingAssessment.record(loaded);
                    assertTrue(issues.stream().noneMatch(issue -> issue.reason().equals("POSITION_DISCONTINUITY")));
                    if (!motion.equals("reverse")) assertTrue(GpsProcessingAssessment.reviewFindings(issues).isEmpty());
                }
    }

    @Test
    void missingElevationAloneRemainsInformational() {
        var points = straight(40, 75, 15);
        points.forEach(point -> point.setZ(Double.NaN));
        var loaded = read(points);
        loaded.trackCleaned = smooth(loaded);
        var original = new TrackDiagnosticEngine().diagnose(GpxDocument.parse("<gpx><trk>" + segment(points) + "</trk></gpx>"));
        assertFalse(original.needsReview());
        assertTrue(original.findings().stream().allMatch(finding -> finding.severity() == TrackFinding.Severity.INFORMATION));
        assertTrue(GpsProcessingAssessment.reviewFindings(GpsProcessingAssessment.record(loaded)).isEmpty());
    }
}
