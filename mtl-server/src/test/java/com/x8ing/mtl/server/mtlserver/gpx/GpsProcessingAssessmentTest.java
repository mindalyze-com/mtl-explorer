package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.*;
import static com.x8ing.mtl.server.mtlserver.gpx.GpsPipelineReliabilityTest.*;
import static org.junit.jupiter.api.Assertions.*;

class GpsProcessingAssessmentTest {
    @Test
    void plausibleTaxiTurnDoesNotBecomeAnOutlierFinding() {
        var input = List.of(c(0, 0, 500, 0), c(0, 162, 500, 15), c(20, 0, 500, 30),
                c(249, 0, 500, 45), c(400, 0, 500, 60));
        var result = read(input);
        assertEquals(input.size(), result.trackCleaned.getNumPoints());
        result.trackCleaned = smooth(result);
        var findings = GpsProcessingAssessment.reviewFindings(GpsProcessingAssessment.record(result));
        assertTrue(findings.stream().noneMatch(f -> f.reason() == POSITION_JUMP));
        assertEquals(1, result.observationEvidence.get(1).canonicalPointIndex());
    }

    @Test
    void elevationFindingsFollowActualRejectionRatherThanAnIndependentHeightThreshold() {
        var fast = straight(20, 20, 1);
        fast.get(10).setZ(560);
        var fastResult = read(fast);
        assertTrue(Double.isNaN(fastResult.trackCleaned.getCoordinateN(10).getZ()));
        var findings = GpsProcessingAssessment.reviewFindings(GpsProcessingAssessment.record(fastResult));
        var rejected = findings.stream().filter(f -> f.reason() == ELEVATION_SPIKE).findFirst().orElseThrow();
        assertEquals(10, rejected.firstPointIndex());
        assertEquals(60, rejected.magnitude());
        assertTrue(rejected.message().contains("cleared"));

        var slow = straight(20, 20, 30);
        slow.get(10).setZ(750);
        var slowResult = read(slow);
        assertEquals(750, slowResult.trackCleaned.getCoordinateN(10).getZ());
        assertTrue(GpsProcessingAssessment.reviewFindings(GpsProcessingAssessment.record(slowResult)).stream()
                .noneMatch(f -> f.reason() == ELEVATION_SPIKE));
    }

    @Test
    void originalPointAndSectionIdentitiesSurviveLenientParsingAndSectionImport() {
        var first = straight(6, 20, 1);
        first.get(1).setY(999);
        first.set(3, c(100000, 0, 500, 3));
        var second = straight(6, 20, 1);
        second.forEach(point -> point.setM(point.getM() + 3600));
        String xml = "<trk>" + segment(first) + segment(second) + "</trk>";
        var loaded = read(xml).getFirst();
        assertEquals(List.of(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
                loaded.observationEvidence.stream().map(GpsObservationEvidence::sourcePointIndex).toList());
        var findings = GpsProcessingAssessment.reviewFindings(GpsProcessingAssessment.record(loaded));
        assertEquals(3, findings.stream().filter(f -> f.reason() == POSITION_JUMP).findFirst().orElseThrow().firstPointIndex());
        assertEquals(1, loaded.observationEvidence.getLast().sourceRecordingSection());
    }

    @Test
    void clockContainmentPreservesCleaningAndFixEvidence() {
        var first = straight(10, 20, 1);
        var second = straight(10, 20, 1);
        second.get(5).setM(second.get(5).getM() - 86400);
        String xml = ("<trk>" + segment(first) + segment(second) + "</trk>")
                .replace("<ele>500.0</ele>", "<ele>500.0</ele><fix>none</fix><sat>3</sat><hdop>8</hdop>");
        var loaded = read(xml);
        assertEquals(2, loaded.size());
        var contained = loaded.get(1);
        assertTrue(contained.gpsTrack.isTimingUnreliable());
        var source = contained.observationEvidence.get(5);
        assertEquals(15, source.sourcePointIndex());
        assertEquals(1, source.sourceRecordingSection());
        assertEquals("none", source.fix());
        assertEquals(5, source.canonicalPointIndex());
        assertTrue(source.decisions().contains("TIMING_UNRELIABLE"));
        assertFalse(source.decisions().contains("TIME_MISSING"));
        assertTrue(Double.isFinite(contained.trackRAW.getCoordinateN(5).getM()));
        assertTrue(Double.isNaN(contained.trackCleaned.getCoordinateN(5).getM()));
        var findings = GpsProcessingAssessment.reviewFindings(GpsProcessingAssessment.record(contained));
        assertTrue(findings.stream().anyMatch(f -> f.reason() == TIMING_UNRELIABLE && f.severity() == TrackFinding.Severity.REVIEW));
    }

    @Test
    void clockFindingIdentityIncludesPointsSkippedBeforeTheExcursion() {
        var points = straight(10, 20, 1);
        points.get(1).setY(999);
        points.get(5).setM(points.get(5).getM() - 86400);
        var result = read(points);
        assertEquals(9, result.trackRAW.getNumPoints());
        assertTrue(result.gpsTrack.isTimingUnreliable());
        assertEquals(5, result.sourceRecordingEvidence.firstExcursionPointIndex());
    }

    @Test
    void verticalFixWarningsUseTheSameSourceQualityThatWeightsSmoothing() {
        String xml = ("<trk>" + segment(straight(10, 20, 1)) + "</trk>")
                .replace("<ele>500.0</ele>", "<ele>500.0</ele><fix>2d</fix><sat>10</sat><hdop>1</hdop>");
        var result = read(xml).getFirst();
        var source = result.observationEvidence.getFirst();
        assertEquals(1, source.uncertaintyScale(false));
        assertEquals(10, source.uncertaintyScale(true));
        var findings = GpsProcessingAssessment.reviewFindings(GpsProcessingAssessment.record(result));
        assertTrue(findings.stream().anyMatch(f -> f.reason() == WEAK_SOURCE_FIX && f.stage() == TrackFinding.Stage.ELEVATION));
        assertTrue(findings.stream().noneMatch(f -> f.reason() == WEAK_SOURCE_FIX && f.stage() == TrackFinding.Stage.POSITION));
    }

    @Test
    void sourceExtensionsCannotOverrideImportIdentity() {
        String xml = "<trk>" + segment(straight(3, 20, 1)).replace("</trkpt>",
                "<extensions><x:observation xmlns:x='" + GpxImportIdentityReader.NAMESPACE
                + "' point='9999' section='9999'/></extensions></trkpt>") + "</trk>";
        var result = read(xml).getFirst();
        assertEquals(List.of(0, 1, 2), result.observationEvidence.stream().map(GpsObservationEvidence::sourcePointIndex).toList());
        assertTrue(result.observationEvidence.stream().allMatch(e -> e.sourceRecordingSection() == 0));
    }
}
