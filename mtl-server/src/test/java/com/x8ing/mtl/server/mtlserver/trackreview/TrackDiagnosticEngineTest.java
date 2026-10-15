package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosticEngine;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackObservation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.*;
import static com.x8ing.mtl.server.mtlserver.trackreview.SyntheticTrackSources.*;
import static org.junit.jupiter.api.Assertions.*;

class TrackDiagnosticEngineTest {
    private final TrackDiagnosticEngine engine = new TrackDiagnosticEngine();

    @Test
    void reportsIndependentProblemsTogetherAndKeepsOriginalObservations() {
        var times = regular(100);
        times.set(10, null);
        times.set(20, times.get(19));
        for (int i = 60; i < times.size(); i++) times.set(i, times.get(i).plusSeconds(1800));
        String xml = new String(source(times), StandardCharsets.UTF_8)
                .replace("lat=\"60.0007500\"", "lat=\"60.0027500\"")
                .replace("lon=\"24.0020000\"><ele>120.5", "lon=\"24.0020000\"><ele>9000");
        var document = GpxDocument.parse(xml);
        var before = document.points();
        var diagnosis = engine.diagnose(document);
        var reasons = diagnosis.findings().stream().map(TrackFinding::reason).toList();
        assertTrue(reasons.containsAll(List.of(TIME_MISSING, TIME_DUPLICATE, RECORDING_GAP)), reasons.toString());
        assertEquals(before, document.points());
        assertEquals(100, diagnosis.pointCount());
        assertEquals(10, diagnosis.findings().stream().filter(f -> f.reason() == TIME_MISSING).findFirst().orElseThrow().firstPointIndex());
        assertFalse(diagnosis.needsReview());
    }

    @Test
    void regularSparseSamplesDoNotImplyMissingPoints() {
        var times = regular(20).stream().map(t -> START.plusSeconds(java.time.Duration.between(START, t).toSeconds() * 600)).toList();
        var result = engine.diagnose(GpxDocument.parse(source(times)));
        assertFalse(result.findings().stream().anyMatch(f -> f.reason() == RECORDING_GAP));
    }

    @Test
    void healthyChronologyIsUntouched() {
        byte[] bytes = source(regular(60));
        byte[] copy = bytes.clone();
        GpxDocument document = GpxDocument.parse(bytes);
        assertFalse(engine.diagnose(document).needsReview());
        assertEquals(regular(60), document.points().stream().map(TrackObservation::timestamp).toList());
        assertArrayEquals(copy, bytes);
    }

    @Test
    void alternatingOldDatesAreDetectedBeforeSplitting() {
        var result = engine.diagnose(GpxDocument.parse(alternatingClockDates()));
        assertTrue(result.needsReview());
        assertEquals(1, result.sections().size());
        assertTrue(result.sections().getFirst().backwardJumpCount() > 80);
        assertTrue(result.findings().stream().anyMatch(f -> f.reason() == TIME_EXCURSION));
        assertTrue(result.findings().stream().anyMatch(f -> f.reason() == TIME_MISSING));
    }

    @Test
    void oneOldEndpointCannotHideBehindLowIssueRatio() {
        var times = regular(5000);
        times.set(4999, START.minusSeconds(20 * 86400L));
        var result = engine.diagnose(GpxDocument.parse(source(times)));
        assertTrue(result.needsReview());
        assertEquals(1, result.sections().getFirst().backwardJumpCount());
    }

    @Test
    void forwardDateExcursionIsAlsoDetected() {
        var times = regular(50);
        times.set(25, START.plusSeconds(40 * 86400L));
        assertTrue(engine.diagnose(GpxDocument.parse(source(times))).findings().stream().anyMatch(f -> f.reason() == TIME_EXCURSION));
    }

    @Test
    void isolatedForwardSpikeIsOneIncidentAndDoesNotPoisonLaterClockOrInventGaps() {
        var times = regular(2000);
        times.set(100, times.get(100).plusSeconds(7200));
        var result = engine.diagnose(GpxDocument.parse(source(times)));
        var incident = result.findings().stream().filter(f -> f.reason() == TIME_EXCURSION).findFirst().orElseThrow();
        assertEquals(1, incident.affectedCount());
        assertEquals(100, incident.firstPointIndex());
        assertEquals(TrackFinding.Severity.REVIEW, incident.severity());
        assertEquals(0, result.sections().getFirst().backwardJumpCount());
        assertFalse(result.findings().stream().anyMatch(f -> f.reason() == RECORDING_GAP || f.reason() == TIMING_UNRELIABLE));
        assertFalse(SourceImportPlan.unreliableTiming(result, 0));
    }

    @Test
    void alertAndSystemicContainmentUseIndependentEvidence() {
        var times = regular(200);
        for (int i = 40; i < 200; i++) times.set(i, times.get(i).minusSeconds(3600));
        var result = engine.diagnose(GpxDocument.parse(source(times)));
        assertTrue(result.needsReview());
        assertEquals(160, result.sections().getFirst().backwardJumpCount());
        assertTrue(result.findings().stream().filter(f -> f.reason() == TIME_BACKWARD).findFirst().orElseThrow().message()
                .startsWith("1 backward clock transition(s)"));
        assertFalse(SourceImportPlan.requiresContainment(result), "Affected observations are not independent clock reversals");
        times = regular(200);
        for (int i : new int[]{40, 80, 120}) times.set(i, times.get(i).minusSeconds(30));
        assertTrue(SourceImportPlan.requiresContainment(engine.diagnose(GpxDocument.parse(source(times)))));
        times = regular(2000);
        for (int i : new int[]{40, 80, 120}) times.set(i, times.get(i).minusSeconds(30));
        assertFalse(SourceImportPlan.requiresContainment(engine.diagnose(GpxDocument.parse(source(times)))), "Systemic coverage must reach one percent");
    }

    @Test
    void ambiguousLocalTimePeakAndMissingContextAreNotAutomaticallyCorrected() {
        for (boolean missing : new boolean[]{false, true}) {
            var times = regular(60);
            times.set(20, times.get(20).plusSeconds(missing ? 7200 : 10));
            if (missing) times.set(19, null);
            var result = engine.diagnose(GpxDocument.parse(source(times)));
            assertTrue(result.findings().stream().anyMatch(f -> f.reason() == TIME_BACKWARD));
            assertFalse(result.findings().stream().anyMatch(f -> f.reason() == TIME_EXCURSION));
            assertFalse(SourceImportPlan.requiresContainment(result));
        }
    }

    @Test
    void repeatedSameDayClockReversalsAreNotMissed() {
        var times = regular(200);
        for (int i = 30; i < 180; i += 3) times.set(i, times.get(i).minusSeconds(120));
        var result = engine.diagnose(GpxDocument.parse(source(times)));
        assertTrue(result.needsReview());
        assertEquals(50, result.sections().getFirst().backwardJumpCount());
    }

    @Test
    void sustainedClockResetPreservesBothRunsAndRequiresReview() {
        var times = regular(100);
        for (int i = 50; i < 100; i++) times.set(i, times.get(i).minusSeconds(3600));
        var result = engine.diagnose(GpxDocument.parse(source(times)));
        assertTrue(result.needsReview());
        assertEquals(TrackFinding.Severity.REVIEW, result.findings().stream().filter(f -> f.reason() == TIME_BACKWARD).findFirst().orElseThrow().severity());
        assertEquals(100, result.pointCount());
        assertEquals(50, result.sections().getFirst().backwardJumpCount());
    }

    @Test
    void longBreakIsReportedAsPossibleMissingObservationsWithoutClaimingCorruption() {
        var times = List.of(Instant.parse("2032-02-18T23:58:00Z"), Instant.parse("2032-02-19T00:02:00Z"),
                Instant.parse("2032-02-20T08:00:00Z"), Instant.parse("2032-02-20T08:01:00Z"));
        var gap = engine.diagnose(GpxDocument.parse(source(times))).findings().stream().filter(f -> f.reason() == RECORDING_GAP).findFirst().orElseThrow();
        assertEquals(TrackFinding.Severity.REVIEW, gap.severity());
        assertTrue(gap.message().contains("paused"));
        assertEquals(1, gap.firstPointIndex());
        assertEquals(2, gap.lastPointIndex());
    }

    @Test
    void sourceSectionsAreEvaluatedIndependently() {
        var old = regular(30).stream().map(t -> t.minusSeconds(20 * 86400L)).toList();
        var result = engine.diagnose(GpxDocument.parse(document(section(regular(30), 0), section(old, 200))));
        assertFalse(result.needsReview());
        assertEquals(2, result.sections().size());
        assertTrue(result.sections().stream().allMatch(s -> s.backwardJumpCount() == 0));
    }

    @Test
    void untimedGeometryAndDuplicateSamplesStayAvailable() {
        var untimed = engine.diagnose(GpxDocument.parse(source(new ArrayList<>(Collections.nCopies(10, null)))));
        assertTrue(untimed.needsReview());
        assertEquals(10, untimed.sections().getFirst().missingTimeCount());
        var times = regular(20);
        times.set(8, times.get(7));
        var result = engine.diagnose(GpxDocument.parse(source(times)));
        assertFalse(result.needsReview());
        assertTrue(result.findings().stream().anyMatch(f -> f.reason() == TIME_DUPLICATE));
    }

    @Test
    void oneSecondBoundaryAndAccumulatedReversalDoNotDependOnFrequency() {
        for (long nanos : new long[]{999_000_000, 1_000_000_000, 1_000_000_001}) {
            var times = regular(2000);
            times.set(100, times.get(99).minusNanos(nanos));
            var result = engine.diagnose(GpxDocument.parse(source(times)));
            assertEquals(nanos > 1_000_000_000, result.needsReview(), "backward nanoseconds=" + nanos);
        }
        var times = regular(20);
        times.set(10, times.get(9).minusMillis(600));
        times.set(11, times.get(9).minusMillis(1200));
        var result = engine.diagnose(GpxDocument.parse(source(times)));
        assertTrue(result.needsReview());
        assertEquals(1.2, result.sections().getFirst().largestBackwardJumpSeconds(), 1e-9);
        times = regular(20);
        times.set(10, null);
        times.set(11, times.get(9).minusSeconds(2));
        assertTrue(engine.diagnose(GpxDocument.parse(source(times))).needsReview(), "A missing point cannot conceal a real clock reversal");
    }

    @Test
    void ordinaryPauseIsInformationalAndTwelveHourBreakRequiresReviewEvenForSparseSampling() {
        for (long gap : new long[]{900, 43200}) {
            var times = regular(20);
            for (int i = 10; i < times.size(); i++) times.set(i, times.get(i).plusSeconds(gap - 5));
            var result = engine.diagnose(GpxDocument.parse(source(times)));
            assertEquals(gap >= 43200, result.needsReview());
        }
        var sparse = List.of(START, START.plusSeconds(43200), START.plusSeconds(86400));
        assertTrue(engine.diagnose(GpxDocument.parse(source(sparse))).needsReview());
    }

    @Test
    void substantialMissingActivityTimeRequiresTenPointsAndOnePercent() {
        for (int missing : new int[]{9, 10}) {
            var times = regular(1000);
            for (int i = 50; i < 50 + missing; i++) times.set(i, null);
            assertEquals(missing >= 10, engine.diagnose(GpxDocument.parse(source(times))).needsReview());
        }
        String route = new String(source(new ArrayList<>(Collections.nCopies(30, null))), StandardCharsets.UTF_8)
                .replace("<trk>", "<rte>").replace("</trk>", "</rte>")
                .replace("<trkseg>", "").replace("</trkseg>", "")
                .replace("trkpt", "rtept");
        assertFalse(engine.diagnose(GpxDocument.parse(route)).needsReview());
    }

    @Test
    void shorterGapsUseEndpointDisplacementAndCumulativeRecordedDistance() {
        for (double step : new double[]{100, 500}) {
            StringBuilder xml = new StringBuilder("<gpx version='1.1'><trk><trkseg>");
            double north = 0;
            long extraSeconds = 0;
            for (int i = 0; i < 100; i++) {
                boolean gap = i == 30 || i == 70;
                if (i > 0) north += gap ? 600 : step;
                if (gap) extraSeconds += 600;
                xml.append(String.format(java.util.Locale.ROOT, "<trkpt lat='%.10f' lon='8'><time>%s</time></trkpt>",
                        47 + north / 111195, START.plusSeconds(i * 20L + extraSeconds)));
            }
            var result = engine.diagnose(GpxDocument.parse(xml.append("</trkseg></trk></gpx>").toString()));
            assertEquals(step == 100, result.needsReview(), "Cumulative missing displacement must also reach five percent");
        }
        var times = regular(20);
        for (int i = 10; i < 20; i++) times.set(i, times.get(i).plusSeconds(900));
        String xml = new String(source(times), StandardCharsets.UTF_8).replace("lat=\"60.0002500\"", "lat=\"60.0202500\"");
        assertTrue(engine.diagnose(GpxDocument.parse(xml)).needsReview(), "A short gap with kilometre-scale displacement requires review");
    }

    @Test
    void fractionalTimesAndTimezoneOffsetsRemainAccurate() {
        String xml = new String(source(regular(3)), StandardCharsets.UTF_8)
                .replace(START.toString(), "2032-02-18T10:00:00.123+02:00");
        var document = GpxDocument.parse(xml);
        assertEquals(START.plusMillis(123), document.points().getFirst().timestamp());
    }

    @Test
    void malformedXmlHasAnExplicitLocationAndExternalEntitiesAreRejected() {
        var error = assertThrows(GpxDocument.ParseFailure.class, () -> GpxDocument.parse("<gpx>"));
        assertTrue(error.getMessage().matches("Could not parse GPX XML at line 1, column [1-9][0-9]*\\."));
        assertThrows(GpxDocument.ParseFailure.class, () -> GpxDocument.parse("<!DOCTYPE gpx [<!ENTITY ext SYSTEM 'file:///nonexistent'>]><gpx xmlns='http://www.topografix.com/GPX/1/1'>&ext;</gpx>"));
    }

    @Test
    void namespaceFreeDemoGpxRetainsItsTrackAndTimestamps() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="PortoTaxiDemo">
                  <metadata><desc>Extracted from dataset: train.csv</desc></metadata>
                  <trk><name>Demo Trip 1404155734620000044</name><trkseg>
                    <trkpt lat="41.14125" lon="-8.614035"><time>2021-05-27T22:22:52Z</time></trkpt>
                    <trkpt lat="41.14145" lon="-8.613835"><time>2021-05-27T22:23:07Z</time></trkpt>
                  </trkseg></trk>
                </gpx>
                """;
        var document = GpxDocument.parse(xml);
        assertEquals(1, document.sections().size());
        assertEquals("TRACK", document.sections().getFirst().kind());
        assertEquals("Demo Trip 1404155734620000044", document.sections().getFirst().name());
        assertEquals(2, document.points().size());
        assertEquals(Instant.parse("2021-05-27T22:22:52Z"), document.points().getFirst().timestamp());
        assertEquals(41.14125, document.points().getFirst().latitude());
        assertEquals(-8.614035, document.points().getFirst().longitude());
    }

    @Test
    void unrelatedXmlNamespaceIsStillRejectedAsGpx() {
        var error = assertThrows(GpxDocument.ParseFailure.class,
                () -> GpxDocument.parse("<gpx xmlns='urn:unrelated' version='1.1'/>"));
        assertEquals("Expected a GPX 1.0 or 1.1 document.", error.getMessage());
    }

    @Test
    void invalidTimestampIsReportedWithoutPretendingThatParsingFailedCompletely() {
        String xml = new String(source(regular(3)), StandardCharsets.UTF_8).replace(START.toString(), "broken-time");
        var document = GpxDocument.parse(xml);
        assertEquals(3, document.points().size());
        assertNull(document.points().getFirst().timestamp());
        assertTrue(engine.diagnose(document).findings().stream().anyMatch(f -> f.reason() == INVALID_VALUE));
    }

    @Test
    void sourceAssessmentDoesNotInventPositionOrElevationCleaningDecisions() {
        String xml = new String(source(regular(20)), StandardCharsets.UTF_8)
                .replace("lat=\"60.0002500\"", "lat=\"61.0002500\"")
                .replace("lon=\"24.0004000\"><ele>120.5", "lon=\"24.0004000\"><ele>9000");
        var findings = engine.diagnose(GpxDocument.parse(xml)).findings();
        assertFalse(findings.stream().anyMatch(f -> f.reason() == POSITION_JUMP));
        assertFalse(findings.stream().anyMatch(f -> f.reason() == ELEVATION_SPIKE));
    }
}
