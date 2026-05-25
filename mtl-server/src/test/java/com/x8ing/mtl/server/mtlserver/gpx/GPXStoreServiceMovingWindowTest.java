package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackStopDetector;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for the moving window metrics calculation.
 * No Spring context required.
 */
class GPXStoreServiceMovingWindowTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    /**
     * Steady climb: 10 points, 10s apart, +10m altitude each, 100m distance each.
     * Window = 90s → all points fit within one window for the center point.
     * Expected ascent per hour: 10m / 10s = 3600 m/h
     */
    @Test
    void steadyClimb_ascentPerHour() {
        List<GpsTrackDataPoint> points = buildTrack(10, 10, 10.0, 100.0);

        GPXStoreService.calculateMovingWindowStats(90, points);

        // center point (index 5) should have a valid elevation gain
        GpsTrackDataPoint center = points.get(5);
        assertNotNull(center.getElevationGainPerHourMovingWindow(), "center should have gain");
        // 10m per 10s = 1 m/s = 3600 m/h
        assertEquals(3600.0, center.getElevationGainPerHourMovingWindow(), 1.0);
        assertNull(center.getElevationLossPerHourMovingWindow(), "no descent expected");
    }

    /**
     * Gradual climb: 20 points, 5s apart, +0.5m altitude each.
     * Each per-segment delta is below the 2m noise threshold, but the aggregate
     * climb is real. The window must still report a positive ascent rate.
     */
    @Test
    void gradualClimb_notDiscardedByThreshold() {
        // 0.5m per 5s = 0.1 m/s = 360 m/h
        List<GpsTrackDataPoint> points = buildTrack(20, 5, 0.5, 50.0);

        GPXStoreService.calculateMovingWindowStats(90, points);

        GpsTrackDataPoint center = points.get(10);
        assertNotNull(center.getElevationGainPerHourMovingWindow(),
                "gradual climb must not be discarded by noise threshold");
        assertEquals(360.0, center.getElevationGainPerHourMovingWindow(), 5.0);
    }

    /**
     * Steady descent: 10 points, 10s apart, -10m altitude each, 100m distance each.
     */
    @Test
    void steadyDescent_lossPerHour() {
        List<GpsTrackDataPoint> points = buildTrack(10, 10, -10.0, 100.0);

        GPXStoreService.calculateMovingWindowStats(90, points);

        GpsTrackDataPoint center = points.get(5);
        assertNotNull(center.getElevationLossPerHourMovingWindow(), "center should have loss");
        assertEquals(3600.0, center.getElevationLossPerHourMovingWindow(), 1.0);
        assertEquals(0.0, center.getElevationGainPerHourMovingWindow(), 0.01, "no ascent expected");
    }

    /**
     * Flat walk: no altitude changes → neither gain nor loss should be set.
     */
    @Test
    void flatWalk_noElevationMetrics() {
        List<GpsTrackDataPoint> points = buildTrack(10, 10, 0.0, 100.0);

        GPXStoreService.calculateMovingWindowStats(90, points);

        GpsTrackDataPoint center = points.get(5);
        assertEquals(0.0, center.getElevationGainPerHourMovingWindow(), 0.01);
        assertNull(center.getElevationLossPerHourMovingWindow());
        // speed should be set: 100m per 10s = 10 m/s = 36 km/h
        assertNotNull(center.getSpeedInKmhMovingWindow());
        assertEquals(36.0, center.getSpeedInKmhMovingWindow(), 0.5);
    }

    /**
     * Small jitter: +1m/-1m alternating. The moving window sums raw deltas
     * (noise filtering is done at the cumulative level, not in the window).
     * Over the window, ascent ≈ descent ≈ ~10m. The window correctly reports
     * this as a low climb rate. This is expected: the window shows instantaneous
     * rate, and over noisy data it will be noisy. Denoising is elsewhere.
     */
    @Test
    void smallJitter_windowReportsRawRate() {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        Instant baseTime = Instant.parse("2025-01-01T10:00:00Z");
        double altitude = 1000.0;

        for (int i = 0; i < 20; i++) {
            GpsTrackDataPoint p = new GpsTrackDataPoint();
            p.setPointTimestamp(Timestamp.from(baseTime.plusSeconds(i * 5L)));
            altitude += (i % 2 == 0) ? 1.0 : -1.0;
            p.setPointAltitude(altitude);
            p.setDistanceInMeterBetweenPoints(i > 0 ? 50.0 : null);
            p.setAscentInMeterBetweenPoints(i > 0 ? ((i % 2 == 0) ? 1.0 : -1.0) : null);
            points.add(p);
        }

        GPXStoreService.calculateMovingWindowStats(90, points);

        // The window will report small ascent/descent rates from the jitter —
        // this is correct; must not crash and values should be small
        GpsTrackDataPoint center = points.get(10);
        if (center.getElevationGainPerHourMovingWindow() != null) {
            assertTrue(center.getElevationGainPerHourMovingWindow() < 1000,
                    "jitter rate should be small");
        }
    }

    /**
     * Boundary: only 2 points → no "both sides" of center → should not crash.
     */
    @Test
    void twoPoints_noCrash() {
        List<GpsTrackDataPoint> points = buildTrack(2, 10, 5.0, 100.0);
        assertDoesNotThrow(() -> GPXStoreService.calculateMovingWindowStats(90, points));
    }

    /**
     * Empty list → should not crash.
     */
    @Test
    void emptyList_noCrash() {
        assertDoesNotThrow(() -> GPXStoreService.calculateMovingWindowStats(90, new ArrayList<>()));
    }

    /**
     * Points with null timestamps → should be skipped without error.
     */
    @Test
    void nullTimestamps_skipped() {
        List<GpsTrackDataPoint> points = buildTrack(5, 10, 5.0, 100.0);
        points.get(2).setPointTimestamp(null);

        assertDoesNotThrow(() -> GPXStoreService.calculateMovingWindowStats(90, points));
    }

    @Test
    void preserveStopAnchors_reinsertsAnchorsDroppedByShapeSimplification() {
        LineString source = GEOMETRY_FACTORY.createLineString(new Coordinate[]{
                xyzm(0, 0, 0),
                xyzm(10, 0, 100),
                xyzm(10, 0, 220),
                xyzm(30, 0, 300)
        });
        LineString simplified = GEOMETRY_FACTORY.createLineString(new Coordinate[]{
                xyzm(0, 0, 0),
                xyzm(30, 0, 300)
        });

        LineString preserved = GPXStoreService.preserveStopAnchors(simplified, source);

        assertEquals(4, preserved.getNumPoints());
        assertEquals(100.0, preserved.getCoordinateN(1).getM());
        assertEquals(220.0, preserved.getCoordinateN(2).getM());
    }

    @Test
    void restoreStopAnchorsAfterSmoothing_repinsMovedAnchorPair() {
        LineString smoothed = GEOMETRY_FACTORY.createLineString(new Coordinate[]{
                xyzm(0, 0, 0),
                xyzm(180, 0, 100),
                xyzm(-160, 0, 220),
                xyzm(300, 0, 300)
        });
        TrackStopDetector.StopRange stop = new TrackStopDetector.StopRange(
                1,
                2,
                100.0,
                220.0,
                0.0,
                0.0,
                555.0,
                TrackStopDetector.StopCategory.BREAK,
                30.0,
                40.0,
                0.9,
                20,
                18);

        LineString restored = GPXStoreService.restoreStopAnchorsAfterSmoothing(smoothed, List.of(stop));

        assertEquals(smoothed.getCoordinateN(0).getX(), restored.getCoordinateN(0).getX(), 1e-12);
        assertEquals(smoothed.getCoordinateN(3).getX(), restored.getCoordinateN(3).getX(), 1e-12);
        assertEquals(0.0, restored.getCoordinateN(1).getX(), 1e-12);
        assertEquals(0.0, restored.getCoordinateN(1).getY(), 1e-12);
        assertEquals(0.0, restored.getCoordinateN(2).getX(), 1e-12);
        assertEquals(0.0, restored.getCoordinateN(2).getY(), 1e-12);
        assertEquals(555.0, restored.getCoordinateN(1).getZ(), 1e-12);
        assertEquals(555.0, restored.getCoordinateN(2).getZ(), 1e-12);
        assertTrue(TrackStopDetector.isStopAnchorPair(restored.getCoordinateN(1), restored.getCoordinateN(2)));
    }

    @Test
    void applyCanonicalDistanceStats_usesPersistedCleanedPointDistances() {
        GpsTrack gpsTrack = new GpsTrack();
        gpsTrack.setTrackLengthInMeter(9999.0);
        gpsTrack.setNumberOfTrackPoints(99);

        List<GpsTrackDataPoint> points = List.of(
                pointWithDistance(null),
                pointWithDistance(12.5),
                pointWithDistance(0.0),
                pointWithDistance(7.5));

        GPXStoreService.applyCanonicalDistanceStats(gpsTrack, points);

        assertEquals(4, gpsTrack.getNumberOfTrackPoints());
        assertEquals(20.0, gpsTrack.getTrackLengthInMeter(), 1e-12);
        assertEquals(12.5, gpsTrack.getMaxDistanceBetweenPoints(), 1e-12);
        assertEquals(7.5, gpsTrack.getMedianDistanceBetweenPoints(), 1e-12);
        assertEquals(20.0 / 3.0, gpsTrack.getAvgDistanceBetweenPoints(), 1e-12);
    }

    @Test
    void calculateThirtySecondStats_usesCanonicalCumulativeAscentAndDistance() {
        List<GpsTrackDataPoint> points = buildTrackForThirtySecondStats();

        var calc = new com.x8ing.mtl.server.mtlserver.metrics.window.PointWindowedRateCalculator(
                com.x8ing.mtl.server.mtlserver.metrics.MetricConstants.DEFAULT_DISPLAY_WINDOW_SEC);
        List<com.x8ing.mtl.server.mtlserver.metrics.window.WindowedRateSample> samples =
                calc.compute(points,
                        com.x8ing.mtl.server.mtlserver.metrics.window.GpsTrackDataPointWindowAdapter.view());

        assertEquals(0.0, samples.get(2).speedInKmh() == null ? 0.0 : samples.get(2).speedInKmh(), 1e-12, "first 30 seconds are warm-up");

        var atThirtySeconds = samples.get(3);
        assertEquals(36.0, atThirtySeconds.speedInKmh(), 0.01);
        assertEquals(1800.0, atThirtySeconds.elevationGainPerHour(), 0.01);
        assertEquals(0.0, atThirtySeconds.elevationLossPerHour(), 0.01);
    }

    @Test
    void applyCanonicalTrackStats_setsFullDensitySummaryFields() {
        GpsTrack gpsTrack = new GpsTrack();
        List<GpsTrackDataPoint> points = buildTrackForThirtySecondStats();
        points.get(3).setSlopePercentageInMovingWindow(7.5);
        points.get(4).setSlopePercentageInMovingWindow(-4.0);

        GPXStoreService.applyCanonicalTrackStats(gpsTrack, points);

        assertEquals(5, gpsTrack.getNumberOfTrackPoints());
        assertEquals(400.0, gpsTrack.getTrackLengthInMeter(), 1e-12);
        assertEquals(20.0, gpsTrack.getAscentInMeter(), 1e-12);
        assertEquals(0.0, gpsTrack.getDescentInMeter(), 1e-12);
        assertEquals(1000.0, gpsTrack.getMinAltitude(), 1e-12);
        assertEquals(1020.0, gpsTrack.getMaxAltitude(), 1e-12);
        assertEquals(36.0, gpsTrack.getSpeedInKmh30sMax(), 0.01);
        assertEquals(1800.0, gpsTrack.getElevationGainPerHour30sMax(), 0.01);
        assertEquals(7.5, gpsTrack.getSlopePercentageMax(), 1e-12);
        assertEquals(-4.0, gpsTrack.getSlopePercentageMin(), 1e-12);
    }

    // --- Helper ---

    /**
     * Build a simple track with evenly spaced points.
     *
     * @param count              number of points
     * @param intervalSeconds    seconds between consecutive points
     * @param altitudeStepMeters altitude change per step (positive = climb, negative = descent)
     * @param distanceStepMeters horizontal distance per step
     */
    private static List<GpsTrackDataPoint> buildTrack(int count, int intervalSeconds,
                                                      double altitudeStepMeters, double distanceStepMeters) {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        Instant baseTime = Instant.parse("2025-01-01T10:00:00Z");
        double altitude = 1000.0;

        for (int i = 0; i < count; i++) {
            GpsTrackDataPoint p = new GpsTrackDataPoint();
            p.setPointTimestamp(Timestamp.from(baseTime.plusSeconds((long) i * intervalSeconds)));
            p.setPointAltitude(altitude);
            if (i > 0) {
                p.setDistanceInMeterBetweenPoints(distanceStepMeters);
                p.setAscentInMeterBetweenPoints(altitudeStepMeters);
            }
            points.add(p);
            altitude += altitudeStepMeters;
        }
        return points;
    }

    private static GpsTrackDataPoint pointWithDistance(Double distanceInMeterBetweenPoints) {
        GpsTrackDataPoint point = new GpsTrackDataPoint();
        point.setDistanceInMeterBetweenPoints(distanceInMeterBetweenPoints);
        return point;
    }

    private static List<GpsTrackDataPoint> buildTrackForThirtySecondStats() {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        Instant baseTime = Instant.parse("2025-01-01T10:00:00Z");
        double ascentSinceStart = 0;

        for (int i = 0; i < 5; i++) {
            GpsTrackDataPoint point = new GpsTrackDataPoint();
            point.setPointTimestamp(Timestamp.from(baseTime.plusSeconds(i * 10L)));
            point.setPointAltitude(1000.0 + i * 5.0);
            point.setDistanceInMeterSinceStart(i * 100.0);
            point.setAscentInMeterSinceStart(ascentSinceStart);
            point.setDescentInMeterSinceStart(0.0);
            if (i > 0) {
                point.setDurationBetweenPointsInSec(10.0);
                point.setDistanceInMeterBetweenPoints(100.0);
                ascentSinceStart += 5.0;
                point.setAscentInMeterSinceStart(ascentSinceStart);
            }
            points.add(point);
        }

        return points;
    }

    private static Coordinate xyzm(double xMeters, double yMeters, double timeS) {
        return new CoordinateXYZM(xMeters / 111_320.0, yMeters / 110_540.0, 100.0, timeS);
    }
}
