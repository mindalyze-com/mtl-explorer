package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.trackreview.SyntheticTrackSources;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GPXReaderClockContainmentTest {
    @Test
    void realXmlImportContainsInterleavedDatesAndSameDayReversalsWithoutDeletingObservations() {
        for (long offset : List.of(9 * 86400L, 6 * 3600L)) {
            var times = SyntheticTrackSources.regular(240);
            for (int i = 60; i < 180; i += 3) times.set(i, times.get(i).minusSeconds(offset));
            times.set(100, null);
            var results = importSource(SyntheticTrackSources.source(times));
            assertEquals(1, results.size(), "Corrupt clocks must not produce separate activities.");
            assertContained(results.getFirst(), times);
            assertTrue(results.getFirst().recordingBreakBeforePointIndexes.isEmpty(),
                    "Clock corruption must not leave artificial gaps in the geometry-only path.");
        }
    }

    @Test
    void isolatedFutureDateExcludesOnlyItsDerivedTimestamp() {
        var times = new java.util.ArrayList<>(SyntheticTrackSources.regular(100).stream()
                .map(t -> t.plusMillis(123)).toList());
        int index = 40;
        times.set(index, times.get(index).plusSeconds(20 * 86400L));
        var results = importSource(SyntheticTrackSources.source(times));
        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(GpsTrack.LOAD_STATUS.SUCCESS, result.gpsTrack.getLoadStatus());
        assertFalse(result.gpsTrack.isTimingUnreliable());
        assertEquals(times.size(), result.trackCleaned.getNumPoints());
        for (int i = 0; i < times.size(); i++) {
            double derivedTime = result.trackCleaned.getCoordinateN(i).getM();
            if (i == index) assertTrue(Double.isNaN(derivedTime));
            else assertEquals(epochSeconds(times.get(i)), derivedTime);
        }
        assertRawTimes(result, times);
    }

    @Test
    void oldEndpointIsContainedEvenWithFewBackwardPoints() {
        var times = new java.util.ArrayList<>(SyntheticTrackSources.regular(100).stream()
                .map(t -> t.plusMillis(123)).toList());
        int index = times.size() - 1;
        times.set(index, times.get(index).minusSeconds(20 * 86400L));
        var results = importSource(SyntheticTrackSources.source(times));
        assertEquals(1, results.size());
        assertContained(results.getFirst(), times);
    }

    @Test
    void independentHealthySectionRetainsMeasuredTimingBesideACorruptClock() {
        var bad = SyntheticTrackSources.regular(120);
        for (int i = 30; i < 90; i += 3) bad.set(i, bad.get(i).minusSeconds(9 * 86400L));
        var healthy = SyntheticTrackSources.regular(120).stream().map(t -> t.minusSeconds(20 * 86400L)).toList();
        var results = importSource(SyntheticTrackSources.document(
                SyntheticTrackSources.section(bad, 0), SyntheticTrackSources.section(healthy, 300)));
        assertEquals(2, results.size());
        assertContained(results.get(0), bad);
        assertFalse(results.get(1).gpsTrack.isTimingUnreliable());
        assertEquals(1, results.get(1).gpsTrack.getSourceRecordingSection());
        assertRawTimes(results.get(1), healthy);
        for (int i = 0; i < healthy.size(); i++)
            assertEquals(epochSeconds(healthy.get(i)), results.get(1).trackCleaned.getCoordinateN(i).getM());
    }

    @Test
    void legitimateForwardActivityGapStillCreatesSeparateActivities() {
        var times = SyntheticTrackSources.regular(60);
        for (int i = 30; i < times.size(); i++) times.set(i, times.get(i).plusSeconds(13 * 3600L));
        var results = importSource(SyntheticTrackSources.source(times));
        assertEquals(2, results.size());
        assertRawTimes(results.get(0), times.subList(0, 30));
        assertRawTimes(results.get(1), times.subList(30, 60));
        assertTrue(results.stream().noneMatch(r -> r.gpsTrack.isTimingUnreliable()));
    }

    @Test
    void missingAndDuplicateTimestampsAreRetainedWithoutInventingClockExcursions() {
        var times = SyntheticTrackSources.regular(60);
        times.set(10, null);
        times.set(30, times.get(29));
        var results = importSource(SyntheticTrackSources.source(times));
        assertEquals(1, results.size());
        assertFalse(results.getFirst().gpsTrack.isTimingUnreliable());
        assertRawTimes(results.getFirst(), times);
    }

    private static List<GPXReader.LoadResult> importSource(byte[] xml) {
        IndexedFile file = new IndexedFile();
        file.setId(1L);
        file.setName("synthetic-clock.gpx");
        return new GPXReader().importGpxXml(file, new String(xml, StandardCharsets.UTF_8));
    }

    private static void assertContained(GPXReader.LoadResult result, List<Instant> times) {
        assertEquals(GpsTrack.LOAD_STATUS.SUCCESS, result.gpsTrack.getLoadStatus());
        assertTrue(result.gpsTrack.isTimingUnreliable());
        assertFalse(result.gpsTrack.isTimingEstimated());
        assertEquals(result.gpsTrack.getStartDate(), result.gpsTrack.getEndDate());
        assertEquals(times.size(), result.trackCleaned.getNumPoints());
        assertTrue(Arrays.stream(result.trackCleaned.getCoordinates()).allMatch(p -> Double.isNaN(p.getM())));
        assertRawTimes(result, times);
    }

    private static void assertRawTimes(GPXReader.LoadResult result, List<Instant> times) {
        assertEquals(times.size(), result.trackRAW.getNumPoints());
        for (int i = 0; i < times.size(); i++) {
            double actual = result.trackRAW.getCoordinateN(i).getM();
            if (times.get(i) == null) assertTrue(Double.isNaN(actual));
            else assertEquals(epochSeconds(times.get(i)), actual);
        }
    }

    private static double epochSeconds(Instant time) {
        return time.getEpochSecond() + time.getNano() / 1_000_000_000.0;
    }
}
