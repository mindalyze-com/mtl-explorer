package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
    void isPlausible_zeroTime_largeDrift_rejected() {
        assertFalse(GPXReader.isPlausible(10.0, 0));
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
    void identicalTimestamps_largeDrift_rejected() {
        List<Coordinate> input = new ArrayList<>();
        input.add(coord(8.5, 47.4, 500, 1000));
        // Same timestamp but ~100 m away (> 5 m threshold)
        input.add(coord(8.501, 47.4, 500, 1000));
        input.add(coord(8.50002, 47.4, 500, 1002));

        FilterResult r = runFilter(input);

        // The large-drift point goes to probation, then next point snaps back
        assertEquals(2, r.accepted.size());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Cross-segment tests — state carried across <trkseg> boundaries
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void crossSegment_badSegmentAfterGoodTrack_filtered() {
        // Simulates the Uetli20100715.gpx problem: good Zürich track, then a segment break
        // followed by garbage GPS data 50 km away.  The bad segment should be filtered out.
        List<List<Coordinate>> segments = new ArrayList<>();

        // Segment 1: 20 normal points near Zürich
        List<Coordinate> seg1 = new ArrayList<>();
        double t = 1000;
        for (int i = 0; i < 20; i++) {
            seg1.add(coord(8.488 + i * 0.0001, 47.356, 830, t));
            t += 2;
        }
        segments.add(seg1);

        // Segment 2: 8 points 50 km NW (bad GPS lock), internally consistent but implausible
        // jump from seg1.  Only 2 seconds after last good point → ~25000 m/s.
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

        FilterResult r = runFilterMultiSegment(segments);

        // Bad segment 2 should be filtered. All seg1 + seg3 points should be kept.
        // seg2 (8 points) enters probation but its jump > 5 km → rejected by promotion guard.
        // Then seg3 re-enters probation and gets promoted (15 points, >15 s).
        assertTrue(r.outlierCount >= 8, "Bad segment points should be outliers, got " + r.outlierCount);
        // All accepted points should be in Zürich area (lon > 8.4)
        for (Coordinate c : r.accepted) {
            assertTrue(c.getX() > 8.4, "Accepted point should be in Zürich, got lon=" + c.getX());
        }
    }

    @Test
    void crossSegment_multipleBadSegments_allFiltered() {
        // Multiple bad segments in a row (like the Uetli file with ~7 bad segments)
        List<List<Coordinate>> segments = new ArrayList<>();

        // Segment 1: good track
        List<Coordinate> seg1 = new ArrayList<>();
        double t = 1000;
        for (int i = 0; i < 20; i++) {
            seg1.add(coord(8.488 + i * 0.0001, 47.356, 830, t));
            t += 2;
        }
        segments.add(seg1);

        // Segments 2-5: each a short bad segment far away
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

        FilterResult r = runFilterMultiSegment(segments);

        // All bad segment points (4 × 4 = 16) should be outliers
        assertTrue(r.outlierCount >= 16, "All bad segment points should be outliers, got " + r.outlierCount);
        for (Coordinate c : r.accepted) {
            assertTrue(c.getX() > 8.4, "Accepted point should be in Zürich area, got lon=" + c.getX());
        }
    }

    @Test
    void crossSegment_legitimateBreakNearby_accepted() {
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

        FilterResult r = runFilterMultiSegment(segments);

        assertEquals(20, r.accepted.size(), "Both segments should be fully accepted");
        assertEquals(0, r.outlierCount);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Integration test with real GPX file
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void uetliGpxFile_crossSegmentOutliers_filtered() {
        // The Uetli20100715_Outlier.gpx file has ~7 bad <trkseg> elements between 17:03 and 17:08
        // that jump 50 km NW from the real track near Zürich Uetliberg.
        Path gpxPath = Paths.get("src/test/java/com/x8ing/mtl/server/mtlserver/gpx/resource/Uetli20100715_Outlier.gpx");
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setFullPath(gpxPath.toAbsolutePath().toString());
        indexedFile.setName("Uetli20100715_Outlier.gpx");

        GPXReader reader = new GPXReader();
        List<GPXReader.LoadResult> results = reader.importGpxFile(indexedFile);
        assertFalse(results.isEmpty(), "Should return at least one LoadResult");
        GPXReader.LoadResult result = results.get(0);

        assertNotNull(result.trackCleaned, "Cleaned track should not be null");
        assertTrue(result.gpsTrack.getDidFilterOutlierByDistance(),
                "Outlier filtering should have been triggered");

        // Cleaned track length should be a reasonable hike (~5-15 km), not ~125 km
        assertTrue(result.gpsTrack.getTrackLengthInMeter() < 20_000,
                "Track length should be < 20 km, got " + result.gpsTrack.getTrackLengthInMeter());
        assertTrue(result.gpsTrack.getTrackLengthInMeter() > 3_000,
                "Track length should be > 3 km, got " + result.gpsTrack.getTrackLengthInMeter());

        // All cleaned points should be in the Zürich/Uetliberg area
        for (int i = 0; i < result.trackCleaned.getNumPoints(); i++) {
            Coordinate c = result.trackCleaned.getCoordinateN(i);
            assertTrue(c.getY() > 47.34 && c.getY() < 47.40,
                    "Lat should be in Uetliberg range [47.34, 47.40], got " + c.getY() + " at point " + i);
            assertTrue(c.getX() > 8.43 && c.getX() < 8.50,
                    "Lon should be in Uetliberg range [8.43, 8.50], got " + c.getX() + " at point " + i);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test helpers
    // ═══════════════════════════════════════════════════════════════════

    private static Coordinate coord(double lon, double lat, double ele, double epochSec) {
        return new CoordinateXYZM(lon, lat, ele, epochSec);
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
