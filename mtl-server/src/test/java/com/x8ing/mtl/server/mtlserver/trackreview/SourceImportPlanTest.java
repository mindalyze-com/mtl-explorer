package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosticEngine;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SourceImportPlanTest {
    @Test
    void singleBackwardAlertKeepsSupportedCanonicalTiming() {
        var times = SyntheticTrackSources.regular(100);
        times.set(40, times.get(39).minusMillis(1001));
        var assessed = assessment(SyntheticTrackSources.source(times));
        assertTrue(assessed.diagnosis().needsReview());
        assertFalse(SourceImportPlan.requiresContainment(assessed.diagnosis()));
        var result = SourceImportPlan.load(file(), assessed).getFirst();
        assertFalse(result.gpsTrack.isTimingUnreliable());
        assertTrue(result.gpsTrack.getStartDate().before(result.gpsTrack.getEndDate()));
        assertTrue(Arrays.stream(result.trackCleaned.getCoordinates()).filter(p -> Double.isFinite(p.getM())).count() > 90);
        assertEquals(times.get(40).toEpochMilli() / 1000.0, result.trackRAW.getCoordinateN(40).getM(), 0.001);
    }

    @Test
    void isolatedForwardSpikeExcludesOnlyItsDerivedTimeAndPreservesSourceIdentity() {
        var times = SyntheticTrackSources.regular(100);
        times.set(40, times.get(40).plusSeconds(7200));
        var assessed = assessment(SyntheticTrackSources.source(times));
        var result = SourceImportPlan.load(file(), assessed).getFirst();
        assertTrue(assessed.diagnosis().needsReview());
        assertFalse(SourceImportPlan.unreliableTiming(assessed.diagnosis(), 0));
        assertFalse(result.gpsTrack.isTimingUnreliable());
        assertEquals(100, result.trackRAW.getNumPoints());
        for (int i = 0; i < 100; i++) assertEquals(times.get(i).getEpochSecond(), result.trackRAW.getCoordinateN(i).getM());
        var spike = result.observationEvidence.stream().filter(p -> p.sourcePointIndex() == 40).findFirst().orElseThrow();
        assertTrue(spike.decisions().contains("TIME_EXCURSION"));
        assertFalse(spike.decisions().contains("TIME_MISSING"));
        assertNotNull(spike.canonicalPointIndex());
        assertTrue(Double.isNaN(result.trackCleaned.getCoordinateN(spike.canonicalPointIndex()).getM()));
        assertEquals(99, Arrays.stream(result.trackCleaned.getCoordinates()).filter(p -> Double.isFinite(p.getM())).count());
        assertEquals(Date.from(times.getFirst()), result.gpsTrack.getStartDate());
        assertEquals(Date.from(times.getLast()), result.gpsTrack.getEndDate());
        assertDoesNotThrow(() -> com.x8ing.mtl.server.mtlserver.gpx.GpsProcessingAssessment.record(result));
    }
    @Test
    void namespaceFreeRouteCanBePreparedForImport() {
        String xml = """
                <gpx version="1.1" creator="synthetic"><rte><name>Route without namespace</name>
                  <rtept lat="41.1400" lon="-8.6100"><time>2024-01-01T10:00:00Z</time></rtept>
                  <rtept lat="41.1410" lon="-8.6090"><time>2024-01-01T10:01:00Z</time></rtept>
                  <rtept lat="41.1420" lon="-8.6080"><time>2024-01-01T10:02:00Z</time></rtept>
                </rte></gpx>
                """;
        var assessed = assessment(xml.getBytes(StandardCharsets.UTF_8));
        assertEquals("ROUTE", assessed.document().sections().getFirst().kind());
        var loaded = SourceImportPlan.load(file(), assessed);
        assertEquals(1, loaded.size());
        assertEquals(GpsTrack.LOAD_STATUS.SUCCESS, loaded.getFirst().gpsTrack.getLoadStatus());
        assertEquals(3, loaded.getFirst().trackRAW.getNumPoints());
    }

    @Test
    void untimedAndSingleInstantSectionsStayIncludedWithoutPretendingToHaveMeasuredTiming() {
        for (List<Instant> times : List.of(
                new ArrayList<Instant>(Collections.nCopies(60, null)),
                new ArrayList<>(Collections.nCopies(60, SyntheticTrackSources.START)))) {
            var source = assessment(SyntheticTrackSources.document(SyntheticTrackSources.section(times, 0),
                    SyntheticTrackSources.section(SyntheticTrackSources.regular(60), 100)));
            assertEquals(times.getFirst() == null, source.diagnosis().needsReview(), "Missing activity time requires review; equal timestamps alone do not.");
            assertFalse(SourceImportPlan.requiresContainment(source.diagnosis()), "Keep the existing route importer.");
            assertTrue(SourceImportPlan.unreliableTiming(source.diagnosis(), 0));
            assertFalse(SourceImportPlan.unreliableTiming(source.diagnosis(), 1));
            assertEquals(120, source.document().points().size());
        }
    }

    @Test
    void repeatedDateCorruptionIsContainedAndRawTimestampsAreRetained() {
        byte[] bytes = SyntheticTrackSources.alternatingClockDates();
        var assessed = assessment(bytes);
        assertTrue(SourceImportPlan.requiresContainment(assessed.diagnosis()));
        var loaded = SourceImportPlan.load(file(), assessed);
        assertEquals(1, loaded.size(), "Date errors must not produce hundreds of derived recordings.");
        var result = loaded.getFirst();
        assertEquals(GpsTrack.LOAD_STATUS.SUCCESS, result.gpsTrack.getLoadStatus());
        assertEquals(600, result.trackRAW.getNumPoints());
        for (int i = 0; i < 600; i++) {
            Instant expected = assessed.document().points().get(i).timestamp();
            double actual = result.trackRAW.getCoordinateN(i).getM();
            if (expected == null) assertTrue(Double.isNaN(actual));
            else assertEquals(expected.getEpochSecond(), actual);
        }
        for (var point : result.trackCleaned.getCoordinates()) assertTrue(Double.isNaN(point.getM()));
        assertTrue(result.gpsTrack.isTimingUnreliable());
        assertEquals(result.gpsTrack.getStartDate(), result.gpsTrack.getEndDate());
        assertEquals(GpxDocument.hash(bytes), assessed.document().sourceHash());
    }

    @Test
    void oneBadEndpointDoesNotExpandTheCanonicalTimeDomain() {
        var times = SyntheticTrackSources.regular(100);
        times.set(99, times.get(99).minusSeconds(7 * 86400));
        var result = SourceImportPlan.load(file(), assessment(SyntheticTrackSources.source(times))).getFirst();
        assertEquals(100, result.trackRAW.getNumPoints());
        assertTrue(result.gpsTrack.isTimingUnreliable());
        assertEquals(result.gpsTrack.getStartDate(), result.gpsTrack.getEndDate());
    }

    @Test
    void healthyRecordingBesideBadRecordingKeepsMeasuredTime() {
        var bad = SyntheticTrackSources.regular(60);
        bad.set(59, bad.get(59).minusSeconds(7 * 86400));
        byte[] bytes = SyntheticTrackSources.document(SyntheticTrackSources.section(bad, 0),
                SyntheticTrackSources.section(SyntheticTrackSources.regular(60), 100));
        var loaded = SourceImportPlan.load(file(), assessment(bytes));
        assertEquals(2, loaded.size());
        assertTrue(loaded.get(0).gpsTrack.isTimingUnreliable());
        assertFalse(loaded.get(1).gpsTrack.isTimingUnreliable());
        assertEquals(1, loaded.get(1).gpsTrack.getSourceRecordingSection());
        assertTrue(Arrays.stream(loaded.get(1).trackCleaned.getCoordinates()).allMatch(p -> Double.isFinite(p.getM())));
    }

    @Test
    void generatedTimesStayExplicitAndCannotBecomeMeasuredRatesOnReimport() {
        byte[] source = SyntheticTrackSources.document(SyntheticTrackSources.estimatedSection(SyntheticTrackSources.regular(600), 0));
        var loaded = SourceImportPlan.load(file(), assessment(source));
        assertEquals(1, loaded.size());
        assertTrue(loaded.getFirst().gpsTrack.isTimingEstimated());
        assertTrue(loaded.getFirst().gpsTrack.isTimingUnreliable());
        assertTrue(Arrays.stream(loaded.getFirst().trackCleaned.getCoordinates()).allMatch(p -> Double.isNaN(p.getM())));
    }

    @Test
    void healthyAndLegitimateLongRecordingsDoNotEnterContainment() {
        var times = SyntheticTrackSources.regular(60);
        for (int i = 30; i < times.size(); i++) times.set(i, times.get(i).plusSeconds(2 * 86400));
        assertFalse(SourceImportPlan.requiresContainment(assessment(SyntheticTrackSources.source(times)).diagnosis()));
    }

    private static SourceReviewService.AssessedSource assessment(byte[] bytes) {
        var document = GpxDocument.parse(bytes);
        return new SourceReviewService.AssessedSource(null, document, new TrackDiagnosticEngine().diagnose(document), List.of());
    }

    private static IndexedFile file() {
        var file = new IndexedFile();
        file.setId(1L);
        file.setIndex("GPS");
        file.setName("synthetic.gpx");
        file.setFullPath("/synthetic/not-read.gpx");
        file.setLastModifiedDate(Date.from(SyntheticTrackSources.START));
        return file;
    }
}
