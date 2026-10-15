package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.*;
import static org.junit.jupiter.api.Assertions.*;

class TrackProcessingDiagnosticEngineTest {
    private final TrackProcessingDiagnosticEngine engine = new TrackProcessingDiagnosticEngine();

    @Test
    void healthyMidnightMultiDaySparseAndExplicitSectionsKeepTheirDiagnosis() {
        for (var times : List.of(SyntheticTrackSources.regular(200),
                List.of(Instant.parse("2032-02-18T23:59:59Z"), Instant.parse("2032-02-19T00:00:01Z")),
                List.of(SyntheticTrackSources.START, SyntheticTrackSources.START.plusSeconds(4 * 86400)))) {
            var source = source(SyntheticTrackSources.document(SyntheticTrackSources.section(times, 0),
                    SyntheticTrackSources.section(times, 1000)));
            var processed = IntStream.range(0, 2).mapToObj(i -> recording(source, i, times.getFirst(), times.getLast(),
                    TrackDiagnosticEngine.seconds(times.getFirst(), times.getLast()), source.sections().get(i).geometryLengthMetres())).toList();
            assertEquals(source, engine.diagnose(source, processed));
        }
    }

    @Test
    void manyDerivedTracksAloneWarnWithoutWithholdingAHealthySource() {
        var source = regular();
        var fragment = recording(source, 0, SyntheticTrackSources.START, SyntheticTrackSources.START.plusSeconds(5), 5.0, 3.0);
        var result = engine.diagnose(source, Collections.nCopies(12, fragment));
        assertFalse(result.needsReview());
        var finding = finding(result, Reason.EXCESSIVE_FRAGMENTATION);
        assertEquals(Severity.INFORMATION, finding.severity());
        assertEquals(12, finding.affectedCount());
        assertEquals(12.0, finding.magnitude());
    }

    @Test
    void damagedClockAndHundredsOfLegacyFragmentsAreReportedSeparately() {
        var source = source(SyntheticTrackSources.alternatingClockDates());
        var fragment = new ProcessedRecording(null, null, SyntheticTrackSources.START, SyntheticTrackSources.START, 0.0, 1.0);
        var result = engine.diagnose(source, Collections.nCopies(400, fragment));
        assertTrue(result.needsReview());
        assertEquals(400, finding(result, Reason.EXCESSIVE_FRAGMENTATION).affectedCount());
        assertTrue(result.findings().stream().anyMatch(f -> f.stage() == Stage.TIMING && f.severity() == Severity.REVIEW));
    }

    @Test
    void datesOutsideSourceTimelineHoldOnlyTheirKnownSectionWithoutChangingImportInput() {
        var source = source(SyntheticTrackSources.document(SyntheticTrackSources.section(SyntheticTrackSources.regular(200), 0),
                SyntheticTrackSources.section(SyntheticTrackSources.regular(100), 1000)));
        var bad = recording(source, 0, SyntheticTrackSources.START.minusSeconds(86400), SyntheticTrackSources.START, 5.0, 100.0);
        var result = engine.diagnose(source, List.of(bad));
        assertTrue(result.sections().getFirst().needsReview());
        assertFalse(result.sections().get(1).needsReview());
        assertEquals(86400.0, finding(result, Reason.INCONSISTENT_TIME_DOMAIN).magnitude());
        assertTrue(SourceImportPlan.unreliableTiming(result, 0));
        assertFalse(SourceImportPlan.unreliableTiming(result, 1));
        assertFalse(SourceImportPlan.requiresContainment(result), "A processing error must not strip healthy source timestamps on reimport.");
    }

    @Test
    void reversedProcessedDatesAreDetectedEvenInsideValidSourceBounds() {
        var source = regular();
        var result = engine.diagnose(source, List.of(recording(source, 0, SyntheticTrackSources.START.plusSeconds(20), SyntheticTrackSources.START, 5.0, 10.0)));
        assertEquals(20.0, finding(result, Reason.INCONSISTENT_TIME_DOMAIN).magnitude());
    }

    @Test
    void movingDurationCannotExceedProcessedElapsedTime() {
        var source = regular();
        var result = engine.diagnose(source, List.of(recording(source, 0, SyntheticTrackSources.START, SyntheticTrackSources.START.plusSeconds(20), 200.0, 10.0)));
        assertEquals(180.0, finding(result, Reason.INCONSISTENT_TIME_DOMAIN).magnitude());
    }

    @Test
    void roundedBoundsAndSmallDurationRoundingAreTolerated() {
        var source = regular();
        var result = engine.diagnose(source, List.of(recording(source, 0, SyntheticTrackSources.START.minusMillis(900), SyntheticTrackSources.START.plusSeconds(20), 21.0, 10.0)));
        assertEquals(source, result);
    }

    @Test
    void untimedGeometryDoesNotTreatImporterFallbackDatesAsRecordedEvidence() {
        var source = source(SyntheticTrackSources.source(Collections.nCopies(20, null)));
        assertEquals(source, engine.diagnose(source, List.of(recording(source, 0, Instant.EPOCH, Instant.EPOCH, 0.0, 10.0))));
    }

    @Test
    void staleDerivedVersionsAreNotMisdiagnosedAsPipelineCalculationsOnTheCurrentSource() {
        var source = regular();
        var stale = new ProcessedRecording("older-version", 0, Instant.EPOCH, Instant.EPOCH, 10000.0, 50000.0);
        assertEquals(source, engine.diagnose(source, Collections.nCopies(20, stale)));
    }

    @Test
    void excessCombinedDistanceRequiresReview() {
        var source = regular();
        var result = engine.diagnose(source, List.of(recording(source, 0, SyntheticTrackSources.START, SyntheticTrackSources.START.plusSeconds(20), 20.0, 10000.0)));
        assertEquals(Severity.REVIEW, finding(result, Reason.INCONSISTENT_METRIC_TOTALS).severity());
        assertTrue(result.needsReview());
    }

    @Test
    void successfulReprocessingClearsOldProcessingFindings() {
        var source = regular();
        var failed = TrackProcessingDiagnosticEngine.withFindings(source, List.of(new TrackFinding(Stage.PROCESSING,
                Reason.PROCESSING_FAILED, Severity.REVIEW, -1, null, null, 1, null, null, "Synthetic processing failure", List.of())));
        assertTrue(failed.needsReview());
        assertEquals(source, engine.diagnose(failed, List.of()));
    }

    private static TrackDiagnosis regular() {
        return source(SyntheticTrackSources.source(SyntheticTrackSources.regular(200)));
    }

    private static TrackDiagnosis source(byte[] bytes) {
        return new TrackDiagnosticEngine().diagnose(GpxDocument.parse(bytes));
    }

    private static ProcessedRecording recording(TrackDiagnosis source, Integer section, Instant first, Instant last, Double moving, Double distance) {
        return new ProcessedRecording(source.sourceHash(), section, first, last, moving, distance);
    }

    private static TrackFinding finding(TrackDiagnosis result, Reason reason) {
        return result.findings().stream().filter(f -> f.reason() == reason).findFirst().orElseThrow();
    }
}
