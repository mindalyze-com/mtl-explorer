package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackStopDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.LineString;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the probation-buffer outlier filter in {@link GPXReader}.
 * No Spring context required — tests the static helper and the algorithm logic
 * by constructing synthetic coordinate sequences.
 */
class GPXReaderOutlierTest {

    // ═══════════════════════════════════════════════════════════════════
    //  isPlausible() helper
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void isPlausible_normalSpeed_accepted() {
        // 100 m in 10 s = 10 m/s — well below 416 m/s
        assertTrue(GPXReader.isPlausible(100, 10));
    }

    @Test
    void isPlausible_exactlyAtLimit_accepted() {
        // distance / dt == MAX_PLAUSIBLE_SPEED_MS  → should be accepted (<=)
        double dist = GPXReader.MAX_PLAUSIBLE_SPEED_MS * 5.0;
        assertTrue(GPXReader.isPlausible(dist, 5.0));
    }

    @Test
    void isPlausible_aboveLimit_rejected() {
        // 5000 m in 1 s = 5000 m/s
        assertFalse(GPXReader.isPlausible(5000, 1));
    }

    @Test
    void isPlausible_zeroTime_smallDrift_accepted() {
        assertTrue(GPXReader.isPlausible(3.0, 0));
    }

    @Test
    void isPlausible_zeroTimeUsesGeometrySupportWithoutInferringSpeed() {
        assertTrue(GPXReader.isPlausible(10.0, 0));
        assertFalse(GPXReader.isPlausible(201.0, 0));
    }

    @Test
    void isPlausible_negativeTime_rejected() {
        assertFalse(GPXReader.isPlausible(1.0, -5));
    }

    @Test
    void isPlausible_longPause_smallJump_accepted() {
        // 1-hour pause, only 1 km moved (e.g. parked, then walked away) — should be accepted
        // effectiveDt = min(3600, 120) = 120 s; 1000 / 120 = 8.3 m/s <= 416 m/s
        assertTrue(GPXReader.isPlausible(1_000, 3600));
    }

    @Test
    void isPlausible_longPause_largeJump_rejected() {
        // 30-hour gap + 125 km jump (Davos→Zürich) — previously accepted due to uncapped dt,
        // now rejected because effectiveDt = 120 s; 125000 / 120 = 1042 m/s > 416 m/s
        assertFalse(GPXReader.isPlausible(125_000, 108_670));
    }

    @Test
    void isPlausible_longPause_under50km_rejected() {
        // 50 km in a 2-hour pause — within the speed budget (120 s × 416 m/s ≈ 50 km)
        // BUT exceeds the absolute distance limit (> 5 km) when dt was capped.
        // This prevents far-away bad-GPS points from appearing plausible after long pauses.
        assertFalse(GPXReader.isPlausible(GPXReader.MAX_PLAUSIBLE_SPEED_MS * GPXReader.MAX_DT_SPEED_CHECK_S, 7200));
    }

    @Test
    void isPlausible_longPause_under5km_accepted() {
        // 4 km in a 2-hour pause — within both the speed budget and the absolute distance limit.
        assertTrue(GPXReader.isPlausible(4_000, 7200));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Algorithm-level tests using synthetic coordinate sequences.
    //  We exercise the filter by calling the same logic that
    //  importGpxFile uses, extracted into a testable helper below.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void normalTrack_allPointsRetained() {
        // 10 points, each ~7 m apart, 1 s interval — entirely plausible
        List<Coordinate> input = straightTrack(8.5, 47.4, 10, 1);
        FilterResult r = runFilter(input);

        assertEquals(10, r.accepted.size(), "All points should be accepted");
        assertEquals(0, r.outlierCount);
    }

    @Test
    void singleMidTrackGlitch_discarded() {
        // Normal track with one wild point injected in the middle
        List<Coordinate> input = straightTrack(8.5, 47.4, 20, 2);
        // Replace point 10 with a location 50 km away (impossible in 2 s)
        input.set(10, coord(10.0, 47.4, 500, input.get(10).getM()));

        FilterResult r = runFilter(input);

        // The glitch is placed on probation, then the next normal point snaps back.
        // So we lose only the 1 glitch point.
        assertEquals(19, r.accepted.size());
        assertTrue(r.outlierCount >= 1);
    }

    @Test
    void burstOfGlitches_allDiscarded() {
        List<Coordinate> input = straightTrack(8.5, 47.4, 20, 2);
        // Points 8-11: random jumps, each far from main track AND far from each other
        double baseTime = input.get(8).getM();
        input.set(8, coord(12.0, 50.0, 500, baseTime));
        input.set(9, coord(-5.0, 30.0, 500, baseTime + 2));
        input.set(10, coord(20.0, 60.0, 500, baseTime + 4));
        input.set(11, coord(-10.0, 10.0, 500, baseTime + 6));

        FilterResult r = runFilter(input);

        // All 4 glitch points should be filtered out
        assertEquals(16, r.accepted.size());
        assertTrue(r.outlierCount >= 4);
    }

    @Test
    void tunnelExit_probationPromoted() {
        // Track goes normally for 5 points, then jumps ~3 km (short tunnel/GPS loss),
        // and the new location produces 10 stable points over >15 seconds.
        List<Coordinate> input = new ArrayList<>();
        double t = 1000;

        // Pre-tunnel: 5 normal points near Zurich
        for (int i = 0; i < 5; i++) {
            input.add(coord(8.5 + i * 0.0001, 47.4, 500, t));
            t += 2;
        }

        // Post-tunnel: 10 stable points ~3 km away (implausible: 3000m / 4s = 750 m/s > 416 m/s)
        t += 2;
        // ~3 km south of the original points (within 5 km promotion threshold)
        double newLon = 8.50;
        double newLat = 47.375;
        for (int i = 0; i < 10; i++) {
            input.add(coord(newLon + i * 0.0001, newLat, 540, t));
            t += 2; // 2 s apart
        }

        FilterResult r = runFilter(input);

        // For a ~3 km jump, the speed drops below 416 m/s after ~8 s (2783/8=348).
        // The first 2 post-tunnel points enter probation, then the 3rd becomes directly
        // plausible and "snaps back" (discarding probation). All 5 pre-tunnel points
        // plus 8 surviving post-tunnel points = 13.
        assertEquals(13, r.accepted.size());
        assertTrue(r.outlierCount >= 2);
    }

    @Test
    void tunnelExit_afterStableLongTrack_bothSegmentsKept() {
        // Long established track (20 points), then short tunnel jump (~3 km), then 10 stable points.
        List<Coordinate> input = new ArrayList<>();
        double t = 1000;

        // Pre-tunnel: 20 normal points (exceeds GPS_STARTUP_WINDOW_SIZE)
        for (int i = 0; i < 20; i++) {
            input.add(coord(8.5 + i * 0.0001, 47.4, 500, t));
            t += 2;
        }

        // Tunnel jump: 2 s gap, ~3 km → speed outlier (1500 m/s > 416)
        t += 2;
        double newLon = 8.50;
        double newLat = 47.375;
        for (int i = 0; i < 10; i++) {
            input.add(coord(newLon + i * 0.0001, newLat, 540, t));
            t += 2;
        }

        FilterResult r = runFilter(input);

        // For ~3 km jump, speed drops below threshold after ~8 s. The first 2 post-tunnel
        // points are lost as probation outliers, then the 3rd snaps back as plausible.
        // 20 pre-tunnel + 8 surviving post-tunnel = 28.
        assertEquals(28, r.accepted.size(), "Most points from both segments should be kept");
        assertTrue(r.outlierCount >= 2);
    }

    @Test
    void endOfTrackDuringProbation_pointsDiscarded() {
        List<Coordinate> input = new ArrayList<>();
        double t = 1000;

        // 10 normal points
        for (int i = 0; i < 10; i++) {
            input.add(coord(8.5 + i * 0.0001, 47.4, 500, t));
            t += 2;
        }

        // 3 stable but implausible points (jump, then only 4 s of stability < 15 s trust)
        t += 2;
        for (int i = 0; i < 3; i++) {
            input.add(coord(7.44 + i * 0.0001, 46.95, 540, t));
            t += 2;
        }
        // Track ends here — probation not long enough

        FilterResult r = runFilter(input);

        assertEquals(10, r.accepted.size(), "Probation points should be discarded at end of track");
    }

    @Test
    void longRecordingGapRetainsBothFragmentsWithoutConnectingDistance() {
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setName("anonymized-long-pause-far-tail.gpx");
        indexedFile.setFullPath(indexedFile.getName());

        GPXReader reader = new GPXReader();
        List<GPXReader.LoadResult> results = reader.importGpxXml(
                indexedFile,
                anonymizedLongPauseFarTailGpxXml());

        // A long recording gap does not establish that either observation is wrong.
        // Keep both fragments, without adding the unobserved journey between them.
        assertEquals(2, results.size());
        assertEquals(List.of(32, 6), results.stream().map(r -> r.trackRAW.getNumPoints()).toList());
        assertEquals(List.of(32, 6), results.stream().map(r -> r.trackCleaned.getNumPoints()).toList());
        for (GPXReader.LoadResult result : results) {
            assertEquals(0, result.gpsTrack.getSourceRecordingSection());
            assertTrue(GPXReader.getDistanceOfWGS84(result.trackCleaned) < 5,
                    "Each fragment should contain only its near-stationary observations");
            assertTrue(maxAdjacentDistance(result.trackCleaned) < 1,
                    "Neither fragment may retain an invented jump");
        }
        assertTrue(results.getFirst().gpsTrack.getEndDate().before(results.getLast().gpsTrack.getStartDate()));
    }

    @Test
    void gpsStartupNoise_badFirstPoint_replaced() {
        // First point is at a cached stale location ~3 km away.
        // Then GPS locks and produces good points nearby each other.
        List<Coordinate> input = new ArrayList<>();
        double t = 1000;

        // Stale first point ~3 km south of the real location
        input.add(coord(8.5, 47.375, 10, t));
        t += 2;

        // Real location: Zurich, stable for 20 seconds
        for (int i = 0; i < 12; i++) {
            input.add(coord(8.5 + i * 0.0001, 47.4, 500, t));
            t += 2;
        }

        FilterResult r = runFilter(input);

        // For ~3 km distance, the speed check becomes plausible after ~8 s (2783/8 = 348).
        // The stale first point stays in accepted, the first 3 good points are lost as
        // probation outliers, and the remaining 9 are accepted directly. Total = 10.
        assertEquals(10, r.accepted.size());
        assertTrue(r.outlierCount >= 3);
    }

    @Test
    void backwardsTimestamp_treatedAsOutlier() {
        List<Coordinate> input = new ArrayList<>();
        input.add(coord(8.5, 47.4, 500, 1000));
        input.add(coord(8.5001, 47.4, 500, 1002));
        input.add(coord(8.5002, 47.4, 500, 999)); // backwards!
        input.add(coord(8.5003, 47.4, 500, 1004));

        FilterResult r = runFilter(input);

        // Point with backwards timestamp enters probation, then next good point snaps back
        assertEquals(3, r.accepted.size());
    }

    @Test
    void identicalTimestamps_smallDrift_accepted() {
        List<Coordinate> input = new ArrayList<>();
        input.add(coord(8.5, 47.4, 500, 1000));
        // Next point: same timestamp, ~0.7 m away (< 5 m threshold)
        input.add(coord(8.50001, 47.4, 500, 1000));
        input.add(coord(8.50002, 47.4, 500, 1002));

        FilterResult r = runFilter(input);

        assertEquals(3, r.accepted.size(), "Small drift at identical timestamp should be accepted");
    }

    @Test
    void identicalTimestampsWithOrdinaryMovementKeepGeometry() {
        List<Coordinate> input = new ArrayList<>();
        input.add(coord(8.5, 47.4, 500, 1000));
        // Clock precision can repeat while the recorded path continues moving.
        input.add(coord(8.501, 47.4, 500, 1000));
        input.add(coord(8.5011, 47.4, 500, 1002));

        FilterResult r = runFilter(input);

        assertEquals(3, r.accepted.size());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Probation re-entry through the production importer
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void millisecondFixPairRejoinsWithoutSplittingTrack() throws Exception {
        // Some phone loggers write a stale and a fresh fix milliseconds apart. The fresh fix is
        // 40 m further on and the walk continues from there at the normal 3 s cadence.
        List<Coordinate> input = new ArrayList<>();
        for (int i = 0; i <= 10; i++) input.add(GpsPipelineReliabilityTest.c(i * 4.5, 0, 500, i * 3));
        for (int i = 0; i < 20; i++) input.add(GpsPipelineReliabilityTest.c(85 + i * 4.5, 0, 500, 30.005 + i * 3));

        GPXReader.LoadResult result = GpsPipelineReliabilityTest.read(input);

        // 40 m in 5 ms stays rejected, but the next point within 200 m rejoins the walk.
        assertEquals(List.of(), result.recordingBreakBeforePointIndexes);
        assertEquals(input.size() - 1, result.trackCleaned.getNumPoints());
        assertTrue(result.observationEvidence.stream().noneMatch(e -> e.decisions().contains("POSITION_DISCONTINUITY")));
        var points = GpsPipelineReliabilityTest.metrics(result.trackCleaned, result.recordingBreakBeforePointIndexes);
        assertEquals(85 + 19 * 4.5, points.getLast().getDistanceInMeterSinceStart(), 1.0);
    }

    @Test
    void tenHertzOneSampleJumpStillRejected() {
        List<Coordinate> input = GpsPipelineReliabilityTest.straight(300, 0.5, 0.1);
        // 60 m in 0.1 s; too short for the later isolated-spike cleanup (90 m).
        input.set(150, GpsPipelineReliabilityTest.c(75, 60, 500, 15));

        GPXReader.LoadResult result = GpsPipelineReliabilityTest.read(input);

        assertEquals(List.of(), result.recordingBreakBeforePointIndexes);
        assertEquals(input.size() - 1, result.trackCleaned.getNumPoints());
        assertTrue(maxAdjacentDistance(result.trackCleaned) < 5, "The one-sample jump must not remain");
    }

    @ParameterizedTest
    @CsvSource({"0.1, 60", "0.2, 100"})
    void fastLoggerRejectsShortGlitchClusters(double interval, double sidewaysMetres) {
        var input = GpsPipelineReliabilityTest.straight(300, 0.5, interval);
        for (int i = 150; i < 154; i++)
            input.set(i, GpsPipelineReliabilityTest.c(i * 0.5, sidewaysMetres, 500, i * interval));

        var result = GpsPipelineReliabilityTest.read(input);

        assertEquals(input.size() - 4, result.trackCleaned.getNumPoints());
        assertEquals(List.of(), result.recordingBreakBeforePointIndexes);
        for (Coordinate point : result.trackCleaned.getCoordinates())
            assertEquals(47, point.getY(), 0.000001, "No shifted point may remain");
    }

    @Test
    void observationGapDuringProbationAllowsSupportedRecovery() {
        var input = GpsPipelineReliabilityTest.straight(100, 0.5, 0.1);
        input.add(GpsPipelineReliabilityTest.c(350, 0, 500, 10));
        for (int i = 0; i < 10; i++)
            input.add(GpsPipelineReliabilityTest.c(500 + i * 4.5, 0, 500, 40 + i * 3));

        var result = GpsPipelineReliabilityTest.read(input);

        assertEquals(input.size() - 1, result.trackCleaned.getNumPoints());
        assertEquals(List.of(), result.recordingBreakBeforePointIndexes);
        assertEquals(GpsPipelineReliabilityTest.T + 40, result.trackCleaned.getCoordinateN(100).getM());
    }

    @Test
    void tenHertzJumpBeyond200MetresStillSplits() {
        List<Coordinate> input = new ArrayList<>();
        for (int i = 0; i < 200; i++) input.add(GpsPipelineReliabilityTest.c(i * 0.5, 0, 500, i * 0.1));
        // 300 m in 0.1 s, then a stable 20 s at the new location.
        for (int i = 200; i < 400; i++) input.add(GpsPipelineReliabilityTest.c(300 + i * 0.5, 0, 500, i * 0.1));

        GPXReader.LoadResult result = GpsPipelineReliabilityTest.read(input);

        assertEquals(List.of(200), result.recordingBreakBeforePointIndexes);
        assertEquals(input.size(), result.trackCleaned.getNumPoints());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Cross-segment tests through the production importer
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void crossSegment_distantInternallyConsistentSectionRetainedWithoutBridge() throws Exception {
        // A source section boundary makes the connecting interval unknown.
        // Distance from the previous section alone cannot reject its observations.
        List<List<Coordinate>> segments = new ArrayList<>();

        // Segment 1: 20 normal points near Zürich
        List<Coordinate> seg1 = new ArrayList<>();
        double t = 1000;
        for (int i = 0; i < 20; i++) {
            seg1.add(coord(8.488 + i * 0.0001, 47.356, 830, t));
            t += 2;
        }
        segments.add(seg1);

        // Segment 2: 8 internally consistent points about 50 km away.
        // The apparent jump across the section boundary is not observed travel.
        List<Coordinate> seg2 = new ArrayList<>();
        t += 2;
        for (int i = 0; i < 8; i++) {
            seg2.add(coord(8.045 + i * 0.0001, 47.778, 830, t));
            t += 2;
        }
        segments.add(seg2);

        // Segment 3: back to Zürich area (good data resumes)
        List<Coordinate> seg3 = new ArrayList<>();
        t += 2;
        for (int i = 0; i < 15; i++) {
            seg3.add(coord(8.475 + i * 0.0001, 47.368, 700, t));
            t += 2;
        }
        segments.add(seg3);

        assertIndependentSections(segments, List.of(20, 28));
    }

    @Test
    void crossSegment_multipleIndependentSectionsRetainedWithoutBridges() throws Exception {
        // Several short, distant sections must remain separate observations.
        List<List<Coordinate>> segments = new ArrayList<>();

        // Segment 1: good track
        List<Coordinate> seg1 = new ArrayList<>();
        double t = 1000;
        for (int i = 0; i < 20; i++) {
            seg1.add(coord(8.488 + i * 0.0001, 47.356, 830, t));
            t += 2;
        }
        segments.add(seg1);

        // Segments 2-5: each a short section far away
        for (int s = 0; s < 4; s++) {
            List<Coordinate> badSeg = new ArrayList<>();
            t += 2;
            double badLon = 8.04 + s * 0.01;
            double badLat = 47.75 + s * 0.01;
            for (int i = 0; i < 4; i++) {
                badSeg.add(coord(badLon + i * 0.0001, badLat, 900, t));
                t += 2;
            }
            segments.add(badSeg);
        }

        // Segment 6: good track resumes near original location
        List<Coordinate> segGood = new ArrayList<>();
        t += 2;
        for (int i = 0; i < 15; i++) {
            segGood.add(coord(8.475 + i * 0.0001, 47.368, 700, t));
            t += 2;
        }
        segments.add(segGood);

        assertIndependentSections(segments, List.of(20, 24, 28, 32, 36));
    }

    @Test
    void crossSegment_nearbySectionsRetainedWithoutInventingGapDistance() throws Exception {
        // GPS briefly loses signal (trkseg break), then resumes nearby.
        // Both segments should be fully accepted.
        List<List<Coordinate>> segments = new ArrayList<>();

        List<Coordinate> seg1 = new ArrayList<>();
        double t = 1000;
        for (int i = 0; i < 10; i++) {
            seg1.add(coord(8.488 + i * 0.0001, 47.356, 830, t));
            t += 2;
        }
        segments.add(seg1);

        // 30 s gap, then resume ~50 m further along the trail
        List<Coordinate> seg2 = new ArrayList<>();
        t += 30;
        for (int i = 0; i < 10; i++) {
            seg2.add(coord(8.489 + i * 0.0001, 47.356, 835, t));
            t += 2;
        }
        segments.add(seg2);

        assertIndependentSections(segments, List.of(10));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Production import with synthetic distant sections
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void syntheticSourceFiltersRealOutliersWithinSpansAndExcludesRecordingGaps() throws Exception {
        var first = GpsPipelineReliabilityTest.straight(30, 100, 10);
        first.set(10, GpsPipelineReliabilityTest.c(50000, 0, 500, 100));
        var second = GpsPipelineReliabilityTest.straight(30, 100, 10);
        for (var point : second) {
            point.setX(point.getX() + 200000.0 / 76000);
            point.setM(point.getM() + 3600);
        }
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setFullPath("/synthetic/distant-sections.gpx");
        indexedFile.setName("distant-sections.gpx");

        GPXReader reader = new GPXReader();
        List<GPXReader.LoadResult> results = reader.importGpxXml(indexedFile,
                "<gpx version='1.1' creator='synthetic'><trk>" + GpsPipelineReliabilityTest.segment(first)
                + GpsPipelineReliabilityTest.segment(second) + "</trk></gpx>");
        assertFalse(results.isEmpty(), "Should return at least one LoadResult");
        GPXReader.LoadResult result = results.get(0);

        assertNotNull(result.trackCleaned, "Cleaned track should not be null");
        assertTrue(result.gpsTrack.getDidFilterOutlierByDistance(),
                "Outlier filtering should have been triggered");
        String loadMessages = result.gpsTrack.getLoadMessages();
        assertNotNull(loadMessages);
        assertTrue(loadMessages.contains("Outlier corrector GPXReader distance/probation filter"),
                "Distance/probation outlier count should be attributed to its corrector");
        assertTrue(loadMessages.contains("Outliers Cleared By Corrector"),
                "Distance/probation summary should include the per-corrector count label");
        assertTrue(loadMessages.contains("Outlier corrector BreakStopTreatment"),
                "Break-stop cleanup count should be attributed to its corrector");

        LineString smoothed = GpsPipelineReliabilityTest.smooth(result);
        var points = GpsPipelineReliabilityTest.metrics(smoothed, result.recordingBreakBeforePointIndexes);
        double cleanedLengthInMeter = points.getLast().getDistanceInMeterSinceStart();
        assertTrue(GPXReader.getDistanceOfWGS84(result.trackCleaned) - cleanedLengthInMeter > 100_000,
                "Over 100 km of unobserved connections must be excluded from the distance");
        assertTrue(cleanedLengthInMeter > 3_000,
                "Observed distance should be > 3 km, got " + cleanedLengthInMeter);
        assertFalse(result.recordingBreakBeforePointIndexes.isEmpty());
        for (int index : result.recordingBreakBeforePointIndexes) {
            assertEquals(GpsTrackDataPoint.IntervalStatus.UNKNOWN, points.get(index).getIntervalStatus());
            assertNull(points.get(index).getDistanceInMeterBetweenPoints());
            assertEquals(points.get(index - 1).getDistanceInMeterSinceStart(),
                    points.get(index).getDistanceInMeterSinceStart(), "Recording gaps add no distance");
        }
        var observed = GpsTrackData.buildObservedPath(smoothed, result.recordingBreakBeforePointIndexes);
        assertTrue(observed.getNumGeometries() > 1, "The map must not draw bridges across recording gaps");
        double observedLength = 0;
        for (int i = 0; i < observed.getNumGeometries(); i++) {
            if (observed.getGeometryN(i) instanceof LineString span) {
                assertTrue(maxAdjacentDistance(span) < 5_000, "No distant jump may remain within an observed span");
                observedLength += GPXReader.getDistanceOfWGS84(span);
            }
        }
        assertEquals(cleanedLengthInMeter, observedLength, 1.0,
                "The map geometry and canonical metrics should describe the same observed travel");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test helpers
    // ═══════════════════════════════════════════════════════════════════

    private static void assertIndependentSections(List<List<Coordinate>> sections, List<Integer> breaks) throws Exception {
        StringBuilder xml = new StringBuilder("<trk>");
        sections.forEach(section -> xml.append(GpsPipelineReliabilityTest.segment(section)));
        var results = GpsPipelineReliabilityTest.read(xml.append("</trk>").toString());
        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(sections.stream().mapToInt(List::size).sum(), result.trackCleaned.getNumPoints());
        assertEquals(breaks, result.recordingBreakBeforePointIndexes);
        var points = GpsPipelineReliabilityTest.metrics(
                result.trackCleaned, result.recordingBreakBeforePointIndexes);
        for (int index : breaks) {
            assertEquals(GpsTrackDataPoint.IntervalStatus.UNKNOWN, points.get(index).getIntervalStatus());
            assertNull(points.get(index).getDistanceInMeterBetweenPoints());
        }
        double expectedDistance = sections.stream()
                .mapToDouble(section -> GPXReader.getDistanceOfWGS84(GpsPipelineReliabilityTest.line(section))).sum();
        assertEquals(expectedDistance, points.getLast().getDistanceInMeterSinceStart(), 1.0);
        assertEquals(sections.size(),
                GpsTrackData.buildObservedPath(result.trackCleaned, breaks).getNumGeometries());
    }

    private static Coordinate coord(double lon, double lat, double ele, double epochSec) {
        return new CoordinateXYZM(lon, lat, ele, epochSec);
    }

    private static String anonymizedLongPauseFarTailGpxXml() {
        List<Coordinate> coordinates = new ArrayList<>();
        long baseEpochS = 1_700_000_000L;
        double homeLon = 8.541700;
        double homeLat = 47.376900;
        double tailLon = 8.740000;
        double tailLat = 47.377400;

        for (int i = 0; i < 32; i++) {
            coordinates.add(coord(homeLon + i * 0.0000005, homeLat, 410.0, baseEpochS + i));
        }

        long tailStartEpochS = baseEpochS + 108_821L;
        for (int i = 0; i < 6; i++) {
            coordinates.add(coord(tailLon + i * 0.0000005, tailLat, 410.0, tailStartEpochS + i));
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<gpx version=\"1.1\" creator=\"MTL Explorer test\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        xml.append("  <trk><name>anonymized long pause far tail</name><trkseg>\n");
        for (Coordinate coordinate : coordinates) {
            xml.append(String.format(Locale.ROOT,
                    "    <trkpt lat=\"%.8f\" lon=\"%.8f\"><ele>%.1f</ele><time>%s</time></trkpt>%n",
                    coordinate.getY(),
                    coordinate.getX(),
                    coordinate.getZ(),
                    Instant.ofEpochSecond((long) coordinate.getM())));
        }
        xml.append("  </trkseg></trk>\n");
        xml.append("</gpx>\n");
        return xml.toString();
    }

    private static double maxAdjacentDistance(LineString lineString) {
        double max = 0.0;
        for (int i = 1; i < lineString.getNumPoints(); i++) {
            max = Math.max(max, GPXReader.getDistanceBetweenTwoWGS84(
                    lineString.getCoordinateN(i - 1),
                    lineString.getCoordinateN(i)));
        }
        return max;
    }

    private static TrackStopDetector.StopRange stopRange(double startTimeS, double endTimeS) {
        return new TrackStopDetector.StopRange(
                0,
                1,
                startTimeS,
                endTimeS,
                8.0,
                47.0,
                500.0,
                TrackStopDetector.StopCategory.LONG_BREAK,
                0.0,
                0.0,
                1.0,
                2,
                0);
    }

    /**
     * Generate a straight track with small longitude increments.
     */
    private static List<Coordinate> straightTrack(double startLon, double startLat,
                                                  int count, double intervalSec) {
        List<Coordinate> coords = new ArrayList<>();
        double t = 1000;
        for (int i = 0; i < count; i++) {
            coords.add(coord(startLon + i * 0.0001, startLat, 500, t));
            t += intervalSec;
        }
        return coords;
    }

    /**
     * Run the probation-buffer outlier filter on a list of coordinates (single segment).
     * This mirrors the logic in {@link GPXReader#importGpxFile} without needing
     * a GPX file or Spring context.
     */
    private static FilterResult runFilter(List<Coordinate> input) {
        return runFilterMultiSegment(List.of(input));
    }

    /**
     * Run the probation-buffer outlier filter across multiple segments.
     * State (lastAcceptedPoint, probationBuffer) is carried across segment boundaries,
     * matching the production logic in GPXReader.
     */
    private static FilterResult runFilterMultiSegment(List<List<Coordinate>> segments) {
        List<Coordinate> accepted = new ArrayList<>();
        List<Coordinate> probationBuffer = new ArrayList<>();
        Coordinate lastAcceptedPoint = null;
        int outlierCount = 0;
        double maxDist = 0;
        List<Double> distances = new ArrayList<>();

        for (List<Coordinate> segment : segments) {
            for (Coordinate c : segment) {
                if (lastAcceptedPoint == null) {
                    accepted.add(c);
                    lastAcceptedPoint = c;
                    continue;
                }

                double distMain = GPXReader.getDistanceBetweenTwoWGS84(c, lastAcceptedPoint);
                double dtMain = c.getM() - lastAcceptedPoint.getM();
                boolean isPlausibleMain = GPXReader.isPlausible(distMain, dtMain);

                if (isPlausibleMain) {
                    if (!probationBuffer.isEmpty()) {
                        outlierCount += probationBuffer.size();
                        probationBuffer.clear();
                    }
                    accepted.add(c);
                    lastAcceptedPoint = c;
                    distances.add(distMain);
                    if (distMain > maxDist) maxDist = distMain;
                } else {
                    if (probationBuffer.isEmpty()) {
                        probationBuffer.add(c);
                        outlierCount++;
                    } else {
                        Coordinate lastProb = probationBuffer.get(probationBuffer.size() - 1);
                        double dtProb = c.getM() - lastProb.getM();
                        double distProb = GPXReader.getDistanceBetweenTwoWGS84(c, lastProb);

                        if (GPXReader.isPlausible(distProb, dtProb)) {
                            probationBuffer.add(c);
                            double duration = c.getM() - probationBuffer.get(0).getM();
                            if (duration >= GPXReader.PROBATION_TRUST_TIME_S) {
                                // Guard: reject if too far from main track
                                double jumpFromMainTrack = GPXReader.getDistanceBetweenTwoWGS84(
                                        probationBuffer.get(0), lastAcceptedPoint);
                                if (jumpFromMainTrack > GPXReader.PROBATION_MAX_PROMOTION_DISTANCE_M) {
                                    outlierCount += probationBuffer.size();
                                    probationBuffer.clear();
                                    continue;
                                }
                                // Startup guard
                                if (accepted.size() <= GPXReader.GPS_STARTUP_WINDOW_SIZE) {
                                    accepted.clear();
                                    distances.clear();
                                    maxDist = 0;
                                }
                                accepted.addAll(probationBuffer);
                                for (int i = 1; i < probationBuffer.size(); i++) {
                                    double d = GPXReader.getDistanceBetweenTwoWGS84(
                                            probationBuffer.get(i), probationBuffer.get(i - 1));
                                    distances.add(d);
                                    if (d > maxDist) maxDist = d;
                                }
                                lastAcceptedPoint = c;
                                probationBuffer.clear();
                            }
                        } else {
                            outlierCount += probationBuffer.size();
                            probationBuffer.clear();
                            probationBuffer.add(c);
                        }
                    }
                }
            }
        }

        // End of track: discard remaining probation
        if (!probationBuffer.isEmpty()) {
            outlierCount += probationBuffer.size();
            probationBuffer.clear();
        }

        return new FilterResult(accepted, outlierCount, maxDist, distances);
    }

    record FilterResult(List<Coordinate> accepted, int outlierCount,
                        double maxDistance, List<Double> distances) {
    }

    // ── splitByTemporalGaps unit tests ──────────────────────────────

    @Test
    void splitByTemporalGaps_noGap_returnsSingleSegment() {
        List<Coordinate> coords = new ArrayList<>();
        double baseT = 1_600_000_000.0;
        for (int i = 0; i < 5; i++) {
            coords.add(coord(8.0 + i * 0.001, 47.0, 500.0, baseT + i * 3600));
        }
        List<List<Coordinate>> segments = GPXReader.splitByTemporalGaps(coords);
        assertEquals(1, segments.size());
        assertEquals(5, segments.get(0).size());
    }

    @Test
    void splitByTemporalGaps_oneGap_twoSegments() {
        List<Coordinate> coords = new ArrayList<>();
        double baseT = 1_600_000_000.0;
        coords.add(coord(8.0, 47.0, 500.0, baseT));
        coords.add(coord(8.001, 47.0, 500.0, baseT + 3600));
        coords.add(coord(8.002, 47.0, 500.0, baseT + 7200));
        // gap: 13 hours = 46800 seconds
        coords.add(coord(8.003, 47.0, 500.0, baseT + 7200 + 46800));
        coords.add(coord(8.004, 47.0, 500.0, baseT + 7200 + 46800 + 3600));

        List<List<Coordinate>> segments = GPXReader.splitByTemporalGaps(coords);
        assertEquals(2, segments.size());
        assertEquals(3, segments.get(0).size());
        assertEquals(2, segments.get(1).size());
    }

    @Test
    void splitByTemporalGaps_knownStopAnchorPairOverThreshold_keepsSingleSegment() {
        double baseT = 1_600_000_000.0;
        double longStopDurationS = 13 * 3600.0;
        List<Coordinate> coords = List.of(
                coord(8.0, 47.0, 500.0, baseT),
                coord(8.0, 47.0, 500.0, baseT + longStopDurationS)
        );

        List<List<Coordinate>> segments = GPXReader.splitByTemporalGaps(
                coords,
                List.of(stopRange(baseT, baseT + longStopDurationS)));
        assertEquals(1, segments.size());
        assertEquals(2, segments.get(0).size());
    }

    @Test
    void splitByTemporalGaps_sparseRepeatedRawFixOverThreshold_splitsWithoutStopRange() {
        double baseT = 1_600_000_000.0;
        double gapDurationS = 13 * 3600.0;
        List<Coordinate> coords = List.of(
                coord(8.0, 47.0, 500.0, baseT),
                coord(8.0, 47.0, 500.0, baseT + gapDurationS)
        );

        List<List<Coordinate>> segments = GPXReader.splitByTemporalGaps(coords);
        assertEquals(2, segments.size());
        assertEquals(1, segments.get(0).size());
        assertEquals(1, segments.get(1).size());
    }

    @Test
    void splitByTemporalGaps_gapJustBelowThreshold_noSplit() {
        List<Coordinate> coords = new ArrayList<>();
        double baseT = 1_600_000_000.0;
        double justBelow = 11.9 * 3600; // 42840 seconds
        coords.add(coord(8.0, 47.0, 500.0, baseT));
        coords.add(coord(8.001, 47.0, 500.0, baseT + justBelow));

        List<List<Coordinate>> segments = GPXReader.splitByTemporalGaps(coords);
        assertEquals(1, segments.size());
        assertEquals(2, segments.get(0).size());
    }

    @Test
    void splitByTemporalGaps_multipleGaps_threeSegments() {
        List<Coordinate> coords = new ArrayList<>();
        double baseT = 1_600_000_000.0;
        coords.add(coord(8.0, 47.0, 500.0, baseT));
        coords.add(coord(8.001, 47.0, 500.0, baseT + 3600));
        double t2 = baseT + 3600 + 48 * 3600;
        coords.add(coord(8.002, 47.0, 500.0, t2));
        coords.add(coord(8.003, 47.0, 500.0, t2 + 3600));
        double t3 = t2 + 3600 + 24 * 3600;
        coords.add(coord(8.004, 47.0, 500.0, t3));

        List<List<Coordinate>> segments = GPXReader.splitByTemporalGaps(coords);
        assertEquals(3, segments.size());
        assertEquals(2, segments.get(0).size());
        assertEquals(2, segments.get(1).size());
        assertEquals(1, segments.get(2).size());
    }

    @Test
    void splitByTemporalGaps_nanTimes_noSplit() {
        List<Coordinate> coords = new ArrayList<>();
        coords.add(coord(8.0, 47.0, 500.0, Double.NaN));
        coords.add(coord(8.001, 47.0, 500.0, Double.NaN));
        coords.add(coord(8.002, 47.0, 500.0, Double.NaN));

        List<List<Coordinate>> segments = GPXReader.splitByTemporalGaps(coords);
        assertEquals(1, segments.size());
        assertEquals(3, segments.get(0).size());
    }
}
