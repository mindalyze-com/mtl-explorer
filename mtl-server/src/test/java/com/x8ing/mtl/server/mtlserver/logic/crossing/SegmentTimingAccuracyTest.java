package com.x8ing.mtl.server.mtlserver.logic.crossing;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.gpx.GPXStoreService;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.*;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer;
import net.sf.geographiclib.Geodesic;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.lang.reflect.Method;
import java.util.*;

import static com.x8ing.mtl.server.mtlserver.testsupport.SegmentReadTestSupport.directReads;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Synthetic accuracy regressions for the complete segment calculation contract.
 */
class SegmentTimingAccuracyTest {
    private static final long TRACK_ID = 1L;
    private static final long DATA_ID = 10L;
    private static final long BASE_MS = 1_700_000_000_000L;
    private static final double ORIGIN_LON = 8;
    private static final double ORIGIN_LAT = 47;
    private static final double METERS_PER_LON = 111320 * Math.cos(Math.toRadians(ORIGIN_LAT));
    private static final double METERS_PER_LAT = 111170;
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void ordinaryInterpolatedElapsedTimeIsExactToAMillisecond() {
        var result = run(List.of(point(0, 0, 0d), point(400, 0, 40d)),
                List.of(trigger("B", 300, 0), trigger("A", 100, 0)), 20, List.of());
        var crossings = crossings(result);
        assertEquals(List.of("A", "B"), names(result));
        assertEquals(20, crossings.get(1).timeInSecSinceLastTriggerPoint, 0.002);
        assertNull(crossings.get(0).timeInSecSinceLastTriggerPoint);
        assertEquals(20, result.segmentsStats.getFirst().timeDelta, 0.002);
        assertEquals(1, result.segmentsStats.getFirst().count);
        double expectedDistance = geodesic(crossings.get(0).gpsTrackDataPoint,
                crossings.get(1).gpsTrackDataPoint);
        assertEquals(expectedDistance, crossings.get(1).distanceInMeterSinceLastTriggerPoint, 0.001);
        assertEquals(expectedDistance / 20 * 3.6, crossings.get(1).avgSpeedSinceLastTriggerPoint, 0.001);
    }

    @Test
    void overlappingZonesStillUseChronologicalClosestApproaches() {
        var result = run(List.of(point(0, 0, 0d), point(100, 0, 10d)),
                List.of(trigger("B", 55, 0), trigger("A", 45, 0)), 30, List.of());
        assertEquals(List.of("A", "B"), names(result));
        assertEquals(1, crossings(result).get(1).timeInSecSinceLastTriggerPoint, 0.002);
    }

    @Test
    void separateReturnVisitsProduceDistinctDirectedSegments() {
        var result = run(List.of(point(0, 0, 0d), point(300, 0, 30d), point(0, 0, 60d)),
                List.of(trigger("A", 100, 0), trigger("B", 200, 0)), 10, List.of());
        assertEquals(List.of("A", "B", "B", "A"), names(result));
        assertArrayEquals(new double[]{10, 20, 10},
                crossings(result).stream().skip(1).mapToDouble(c -> c.timeInSecSinceLastTriggerPoint).toArray(), 0.002);
    }

    @Test
    void repeatedIdenticalSamplesAtGateRetainTheArrivalTime() {
        var result = run(List.of(point(0, 0, 0d), point(100, 0, 10d),
                        point(100, 0, 130d), point(200, 0, 140d)),
                List.of(trigger("A", 100, 0), trigger("B", 200, 0)), 10, List.of());
        assertEquals(2, crossings(result).size());
        assertEquals(130, crossings(result).get(1).timeInSecSinceLastTriggerPoint, 0.002);
    }

    @Test
    void eventClippingDoesNotSubstituteForMissingMotionMetadata() {
        var result = run(List.of(point(0, 0, 0d), point(400, 0, 40d)),
                List.of(trigger("A", 100, 0), trigger("B", 300, 0)), 10,
                List.of(stop(5, 15), stop(25, 35)));
        var end = crossings(result).get(1);
        assertEquals(20, end.timeInSecSinceLastTriggerPoint, 0.002);
        assertEquals(0, end.segmentNotesSinceLastTriggerPoint.stopCount);
        assertEquals(0, end.segmentNotesSinceLastTriggerPoint.totalStoppedSec,
                "Events without canonical stationary support cannot invent stopped coverage");
        assertEquals(0, end.segmentNotesSinceLastTriggerPoint.longestStopSec);
        assertFalse(end.segmentNotesSinceLastTriggerPoint.coverageComplete);
    }

    @Test
    void curvedTimedPathUsesFullPolylineDistance() {
        var points = List.of(point(0, 0, 0d), point(10, 0, 10d), point(50, 100, 50d),
                point(90, 0, 90d), point(100, 0, 100d));
        var result = run(points, List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 5, List.of());
        assertEquals(pathDistance(points), crossings(result).get(1).distanceInMeterSinceLastTriggerPoint, 0.001);
        assertEquals(100, crossings(result).get(1).timeInSecSinceLastTriggerPoint, 0.002);
    }

    @Test
    void irregularSamplingUsesEachLocalTimeInterval() {
        var result = run(List.of(point(0, 0, 0d), point(100, 0, 10d), point(200, 0, 50d)),
                List.of(trigger("A", 25, 0), trigger("B", 175, 0)), 5, List.of());
        assertEquals(2.5, (crossings(result).get(0).gpsTrackDataPoint.getPointTimestamp().getTime() - BASE_MS) / 1000d, 0.002);
        assertEquals(37.5, crossings(result).get(1).timeInSecSinceLastTriggerPoint, 0.002);
    }

    @Test
    void localProjectionErrorComparedWithIndependentEllipsoidalMinimum() {
        var a = point(-100, -100, 0d);
        var b = point(100, 100, 100d);
        var marker = trigger("A", -35, 35);
        var result = run(List.of(a, b), List.of(marker), 60, List.of());
        assertEquals(1, crossings(result).size());
        // Minimize GeographicLib distance over the exact same interpolated lon/lat curve.
        // This checks the projection independently instead of reusing the production factor.
        double lo = 0, hi = 1;
        for (int i = 0; i < 70; i++) {
            double left = lo + (hi - lo) / 3;
            double right = hi - (hi - lo) / 3;
            if (distanceAt(a, b, marker, left) < distanceAt(a, b, marker, right)) hi = right;
            else lo = left;
        }
        double reference = (lo + hi) / 2;
        var crossing = crossings(result).getFirst();
        double actual = (crossing.gpsTrackDataPoint.getPointLongLat().getX() - a.getPointLongLat().getX())
                        / (b.getPointLongLat().getX() - a.getPointLongLat().getX());
        double alongTrackError = Math.abs(actual - reference) * geodesic(a, b);
        double timeError = Math.abs(actual - reference) * 100;
        System.out.printf(Locale.ROOT, "REVIEW projection at 47 degrees: along-track error %.6f m; time error %.6f s%n",
                alongTrackError, timeError);
        assertTrue(alongTrackError < 0.001, "Closest approach must agree with the independent ellipsoidal minimum");
    }

    private static double distanceAt(GpsTrackDataPoint a, GpsTrackDataPoint b, TriggerPoint center, double factor) {
        double lon = a.getPointLongLat().getX() + (b.getPointLongLat().getX() - a.getPointLongLat().getX()) * factor;
        double lat = a.getPointLongLat().getY() + (b.getPointLongLat().getY() - a.getPointLongLat().getY()) * factor;
        return Geodesic.WGS84.Inverse(center.coordinate.y, center.coordinate.x, lat, lon).s12;
    }

    @Test
    void closestApproachAgreesWithIndependentMinimumAcrossLatitudesAndBearings() {
        for (double latitude : new double[]{-75, -47, 0, 47, 75}) {
            for (double bearing = 0; bearing < 360; bearing += 45) {
                var first = Geodesic.WGS84.Direct(latitude, 8, bearing + 180, 150);
                var last = Geodesic.WGS84.Direct(latitude, 8, bearing, 150);
                var a = geographicPoint(first.lon2, first.lat2, 0d);
                var b = geographicPoint(last.lon2, last.lat2, 100d);
                double markerLat = first.lat2 + (last.lat2 - first.lat2) * 0.37;
                double markerLon = first.lon2 + (last.lon2 - first.lon2) * 0.37;
                var offset = Geodesic.WGS84.Direct(markerLat, markerLon, bearing + 90, 75);
                var marker = new TriggerPoint("A", new Coordinate(offset.lon2, offset.lat2));
                var result = run(List.of(a, b), List.of(marker), 100, List.of());
                assertEquals(1, crossings(result).size());
                double lo = 0, hi = 1;
                for (int i = 0; i < 70; i++) {
                    double left = lo + (hi - lo) / 3;
                    double right = hi - (hi - lo) / 3;
                    if (distanceAt(a, b, marker, left) < distanceAt(a, b, marker, right)) hi = right;
                    else lo = left;
                }
                double actual = crossings(result).getFirst().trackPointPosition;
                double errorMeters = Math.abs(actual - (lo + hi) / 2) * geodesic(a, b);
                assertTrue(errorMeters < 0.001,
                        "Closest approach error=" + errorMeters + "m at latitude=" + latitude + " bearing=" + bearing);
            }
        }
    }

    @Test
    void importedPointRetainsFractionalSeconds() throws Exception {
        var imported = persistedPoint(new CoordinateXYZM(8, 47, 500, BASE_MS / 1000d + 0.9), 0);
        assertEquals(BASE_MS + 900, imported.getPointTimestamp().getTime(),
                "The row consumed by crossing calculations must retain GPX milliseconds");
    }

    @Test
    void fractionalImportProducesCorrectElapsedTime() throws Exception {
        var a = persistedPoint(new CoordinateXYZM(8, 47, 500, BASE_MS / 1000d + 0.9), 0);
        var b = persistedPoint(new CoordinateXYZM(8.00001, 47, 500, BASE_MS / 1000d + 1.1), 1);
        var result = run(List.of(a, b), List.of(new TriggerPoint("A", new Coordinate(8, 47)),
                new TriggerPoint("B", new Coordinate(8.00001, 47))), 0.2, List.of());
        assertEquals(0.2, crossings(result).get(1).timeInSecSinceLastTriggerPoint, 0.002,
                "A valid 200 ms traversal must not become 1000 ms after import");
    }

    @Test
    void anUntimedTrackMustNotContributeAZeroSecondTimingStatistic() {
        var result = run(List.of(point(0, 0, null), point(100, 0, null)),
                List.of(trigger("A", 20, 0), trigger("B", 80, 0)), 5, List.of());
        assertTrue(result.segmentsStats.isEmpty(),
                "Unavailable timing must be excluded from timed statistics, not counted as zero seconds");
    }

    @Test
    void fractionalInnerUntimedIntervalRetainsGeometryWithoutZeroTiming() {
        var result = run(List.of(point(0, 0, 0d), point(100, 0, 100d),
                        point(200, 0, null), point(300, 0, null), point(400, 0, 400d)),
                List.of(trigger("A", 220, 0), trigger("B", 280, 0)), 5, List.of());
        assertEquals(List.of("A", "B"), names(result));
        var finish = crossings(result).getLast();
        assertTrue(crossings(result).stream().allMatch(c -> c.gpsTrackDataPoint.getPointTimestamp() == null));
        assertEquals(SegmentTimingStatus.MISSING_TIME, finish.timingStatusSinceLastTriggerPoint);
        assertNull(finish.timeInSecSinceLastTriggerPoint);
        assertNull(finish.avgSpeedSinceLastTriggerPoint);
        assertEquals(geodesic(crossings(result).getFirst().gpsTrackDataPoint, finish.gpsTrackDataPoint),
                finish.distanceInMeterSinceLastTriggerPoint, 0.001);
        assertTrue(finish.distanceInMeterSinceLastTriggerPoint > 59);
        assertTrue(result.segmentsStats.isEmpty());
    }

    @Test
    void unreliableTimingRetainsGeometryWithoutPublishingEstimatedTimes() {
        var result = run(List.of(point(0, 0, 0d), point(100, 0, 100d)),
                List.of(trigger("A", 20, 0), trigger("B", 80, 0)), 5, List.of(), true);
        assertEquals(List.of("A", "B"), names(result));
        assertEquals(SegmentTimingStatus.MISSING_TIME, crossings(result).getLast().timingStatusSinceLastTriggerPoint);
        assertTrue(crossings(result).getLast().distanceInMeterSinceLastTriggerPoint > 59);
        assertNull(crossings(result).getLast().timeInSecSinceLastTriggerPoint);
        assertNull(crossings(result).getLast().avgSpeedSinceLastTriggerPoint);
        assertTrue(crossings(result).stream().allMatch(c -> c.gpsTrackDataPoint.getPointTimestamp() == null));
        assertTrue(result.segmentsStats.isEmpty());
    }

    @Test
    void exactTimedEndpointSurvivesAnUntimedNeighbor() {
        var result = run(List.of(point(0, 0, null), point(100, 0, 100d), point(200, 0, 200d)),
                List.of(trigger("A", 100, 0), trigger("B", 200, 0)), 5, List.of());
        var a = crossings(result).stream().filter(c -> c.triggerPoint.name.equals("A")).findFirst().orElseThrow();
        assertEquals(new Date(BASE_MS + 100_000), a.gpsTrackDataPoint.getPointTimestamp(),
                "At interpolation factor 1, the known endpoint time does not depend on the other endpoint");
        assertEquals(List.of("A", "B"), names(result));
        assertEquals(100, crossings(result).get(1).timeInSecSinceLastTriggerPoint, 0.002);
    }

    @Test
    void nearbyDuplicateTimestampsRetainGeometryWithoutInventingInteriorTimes() {
        var result = run(List.of(point(0, 0, 10d), point(100, 0, 10d)),
                List.of(trigger("A", 20, 0), trigger("B", 80, 0)), 5, List.of());
        assertEquals(List.of("A", "B"), names(result));
        assertTrue(crossings(result).stream().allMatch(c -> c.gpsTrackDataPoint.getPointTimestamp() == null));
        assertEquals(SegmentTimingStatus.MISSING_TIME, crossings(result).getLast().timingStatusSinceLastTriggerPoint);
        assertNull(crossings(result).getLast().timeInSecSinceLastTriggerPoint);
        assertTrue(crossings(result).getLast().distanceInMeterSinceLastTriggerPoint > 59);
        assertTrue(result.segmentsStats.isEmpty());
    }

    @Test
    void duplicateTimestampsCannotCreateCrossingsBetweenDistantObservations() {
        var result = run(List.of(point(0, 0, 10d), point(300, 0, 10d)),
                List.of(trigger("A", 60, 0), trigger("B", 240, 0)), 5, List.of());
        assertTrue(crossings(result).isEmpty(), "Equal timestamps do not support geometry across 300 metres");
        assertTrue(result.segmentsStats.isEmpty());

        var endpoints = run(List.of(point(0, 0, 10d), point(300, 0, 10d)),
                List.of(trigger("A", 0, 0), trigger("B", 300, 0)), 5, List.of());
        assertEquals(List.of("A", "B"), names(endpoints));
        assertEquals(SegmentTimingStatus.INVALID_TIME, crossings(endpoints).getLast().timingStatusSinceLastTriggerPoint);
        assertNull(crossings(endpoints).getLast().timeInSecSinceLastTriggerPoint);
        assertTrue(endpoints.segmentsStats.isEmpty());
    }

    @Test
    void coincidentMarkersOnAValidRecordingHaveZeroElapsedWithNoInventedSpeed() {
        var result = run(List.of(point(0, 0, 0d), point(100, 0, 10d)),
                List.of(trigger("A", 50, 0), trigger("B", 50, 0)), 5, List.of());
        var end = crossings(result).get(1);
        assertEquals(SegmentTimingStatus.AVAILABLE, end.timingStatusSinceLastTriggerPoint);
        assertEquals(0d, end.timeInSecSinceLastTriggerPoint);
        assertNull(end.avgSpeedSinceLastTriggerPoint);
        assertEquals(1, result.segmentsStats.getFirst().count);
    }

    @Test
    void clockRollbackPreservesPathOrderAndMarksTheTraversalUnavailable() {
        var result = run(List.of(point(0, 0, 20d), point(100, 0, 10d)),
                List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 5, List.of());
        assertEquals(List.of("A", "B"), names(result));
        assertEquals(SegmentTimingStatus.INVALID_TIME, crossings(result).get(1).timingStatusSinceLastTriggerPoint);
        assertNull(crossings(result).get(1).timeInSecSinceLastTriggerPoint);
        assertTrue(result.segmentsStats.isEmpty());
    }

    @Test
    void explicitUntimedRecordingBreakNeverCreatesAConnectingCrossing() {
        var gap = new GpsTrackEvent();
        gap.setEventType(GpsTrackEvent.EVENT_TYPE.GPS_GAP);
        gap.setEndPointIndex(1);
        var result = run(List.of(point(0, 0, null), point(100, 0, null)),
                List.of(trigger("A", 40, 0), trigger("B", 60, 0)), 5, List.of(gap));
        assertTrue(crossings(result).isEmpty());
    }

    @Test
    void untimedInteriorGeometryMustStillContributeDistance() {
        var points = List.of(point(0, 0, 0d), point(10, 0, 10d), point(50, 100, null),
                point(90, 0, 90d), point(100, 0, 100d));
        var result = run(points, List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 5, List.of());
        assertEquals(pathDistance(points), crossings(result).get(1).distanceInMeterSinceLastTriggerPoint, 0.001,
                "Missing time at the bend does not erase its observed geometry");
    }

    @Test
    void aTenMinuteRecordingBreakMustNotBecomeAConfidentTimedTraversal() throws Exception {
        // The two recorded sections remain entirely west/east of both requested markers.
        // No observations exist between the sections, so their connecting chord is unobserved.
        String xml = """
                <gpx version="1.1" creator="MTL Explorer synthetic review" xmlns="http://www.topografix.com/GPX/1/1">
                  <trk><trkseg>
                    <trkpt lat="47" lon="8"><time>2026-01-01T00:00:00Z</time></trkpt>
                    <trkpt lat="47" lon="8.0001"><time>2026-01-01T00:00:10Z</time></trkpt>
                  </trkseg><trkseg>
                    <trkpt lat="47" lon="8.01"><time>2026-01-01T00:10:10Z</time></trkpt>
                    <trkpt lat="47" lon="8.0101"><time>2026-01-01T00:10:20Z</time></trkpt>
                  </trkseg></trk>
                </gpx>
                """;
        IndexedFile file = new IndexedFile();
        file.setName("synthetic-recording-gap.gpx");
        file.setFullPath("/synthetic-recording-gap.gpx");
        var loaded = new GPXReader().importGpxXml(file, xml);
        assertEquals(1, loaded.size(), "Confirm the real importer retains the recording gap within one track");
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (Coordinate coordinate : loaded.getFirst().trackCleaned.getCoordinates()) {
            points.add(persistedPoint(coordinate, points.size()));
        }
        var result = run(points, List.of(new TriggerPoint("A", new Coordinate(8.0034, 47)),
                new TriggerPoint("B", new Coordinate(8.0067, 47))), 5, List.of());
        System.out.println("REVIEW gap traversal elapsed seconds: " + result.segmentsStats.stream().map(s -> s.timeDelta).toList());
        assertTrue(result.segmentsStats.isEmpty(),
                "A crossing-quality policy must exclude or explicitly flag interpolations across a recording break");
    }

    @Test
    void dateLineTraversalUsesTheShortLongitudeArc() {
        var a = geographicPoint(179.999, 47, 0d);
        var b = geographicPoint(-179.999, 47, 20d);
        var result = run(List.of(a, b), List.of(new TriggerPoint("A", new Coordinate(179.9995, 47)),
                new TriggerPoint("B", new Coordinate(-179.9995, 47))), 10, List.of());
        assertEquals(2, crossings(result).size(), "Both markers lie on the approximately 152 metre date-line crossing");
        assertEquals(10, crossings(result).get(1).timeInSecSinceLastTriggerPoint, 0.002);
    }

    @Test
    void distinctNamedPairsMustNotMergeTheirTimingStatistics() {
        var result = run(List.of(point(0, 0, 0d), point(400, 0, 40d)),
                List.of(trigger("A-B", 50, 0), trigger("C", 100, 0),
                        trigger("A", 200, 0), trigger("B-C", 300, 0)), 5, List.of());
        assertEquals(3, result.segmentsStats.size(),
                "Pair (A-B,C) and pair (A,B-C) must not share an aggregation key");
    }

    @Test
    void canonicalMotionCoverageDoesNotDependOnEventTimestampOrder() {
        var points = List.of(point(0, 0, 0d), point(100, 0, 10d), point(200, 0, 15d),
                point(200, 0, 85d), point(300, 0, 100d), point(400, 0, 110d));
        points.get(3).setMotionState(com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.STOPPED);
        var result = run(points, List.of(trigger("A", 100, 0), trigger("B", 300, 0)), 10,
                List.of(stop(100, 120), stop(15, 85)));
        var notes = crossings(result).getLast().segmentNotesSinceLastTriggerPoint;
        assertEquals(1, notes.stopCount);
        assertEquals(70d, notes.totalStoppedSec);
        assertEquals(70d, notes.longestStopSec);
    }

    @Test
    void nearbyDuplicateTimestampRetainsTheWholeObservedTraversal() {
        var points = List.of(point(0, 0, 0d), point(40, 0, 4d), point(41, 0, 4d), point(100, 0, 10d));
        points.get(2).setIntervalStatus(GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY);
        var result = run(points, List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 2, List.of());
        var end = crossings(result).getLast();
        assertEquals(SegmentTimingStatus.AVAILABLE, end.timingStatusSinceLastTriggerPoint);
        assertEquals(10, end.timeInSecSinceLastTriggerPoint, 0.002);
        assertEquals(pathDistance(points), end.distanceInMeterSinceLastTriggerPoint, 0.001);
        assertEquals(1, result.segmentsStats.getFirst().count);
    }

    @Test
    void duplicateIntervalCannotInventAnInteriorCrossingTimestamp() {
        var result = run(List.of(point(0, 0, 10d), point(4, 0, 10d)),
                List.of(trigger("A", 1, 0), trigger("B", 3, 0)), 0.1, List.of());
        assertEquals(List.of("A", "B"), names(result));
        assertEquals(SegmentTimingStatus.MISSING_TIME, crossings(result).getLast().timingStatusSinceLastTriggerPoint);
        assertNull(crossings(result).getFirst().gpsTrackDataPoint.getPointTimestamp());
        assertTrue(result.segmentsStats.isEmpty());
    }

    @Test
    void storedUnsupportedIntervalSplitsEvenWithShortIncreasingTimestamps() {
        var points = List.of(point(0, 0, 0d), point(100, 0, 10d));
        points.getLast().setIntervalStatus(GpsTrackDataPoint.IntervalStatus.UNKNOWN);
        var result = run(points, List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 2, List.of());
        assertEquals(SegmentTimingStatus.AVAILABLE, crossings(result).getLast().timingStatusSinceLastTriggerPoint);
        assertTrue(crossings(result).getLast().recordingGapSinceLastTriggerPoint);
        assertNull(crossings(result).getLast().distanceInMeterSinceLastTriggerPoint);
    }

    @Test
    void timeGapLimitIsInclusiveAtMillisecondPrecision() {
        for (double seconds : List.of(119.999, 120.0, 120.001)) {
            var result = run(List.of(point(0, 0, 0d), point(100, 0, seconds)),
                    List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 2, List.of());
            assertEquals(SegmentTimingStatus.AVAILABLE, crossings(result).getLast().timingStatusSinceLastTriggerPoint);
            assertEquals(seconds > 120, crossings(result).getLast().recordingGapSinceLastTriggerPoint);
            assertEquals(seconds, crossings(result).getLast().timeInSecSinceLastTriggerPoint, 0.001);
        }
    }

    @Test
    void aStopEventCannotOverrideSparseUnsupportedRecording() {
        var points = List.of(point(0, 0, 0d), point(50, 0, 5d), point(50, 0, 185d), point(100, 0, 190d));
        var event = stop(5, 185);
        event.setStartPointIndex(1);
        event.setEndPointIndex(2);
        var result = run(points, List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 2, List.of(event));
        assertEquals(SegmentTimingStatus.AVAILABLE, crossings(result).getLast().timingStatusSinceLastTriggerPoint);
        assertTrue(crossings(result).getLast().recordingGapSinceLastTriggerPoint);
        assertNull(crossings(result).getLast().distanceInMeterSinceLastTriggerPoint);
    }

    @Test
    void zoneEdgeScatterRemainsOneVisitWithoutSameZoneSegments() {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int i = 0; i < 20; i++) points.add(point(0, i % 2 == 0 ? 19.5 : 20.5, (double) i));
        points.add(point(100, 0, 30d));
        var result = run(points, List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 20, List.of());
        assertEquals(List.of("A", "B"), names(result));
        assertEquals(1, result.segmentsStats.size());
        assertEquals("A - B", result.segmentsStats.getFirst().label);
        assertEquals(30, crossings(result).getLast().timeInSecSinceLastTriggerPoint, 0.002);
    }

    @Test
    void genuineDepartureAndReturnStillProducesTwoVisits() {
        var result = run(List.of(point(0, 0, 0d), point(30, 0, 10d), point(0, 0, 20d)),
                List.of(trigger("A", 0, 0)), 20, List.of());
        assertEquals(List.of("A", "A"), names(result));
        assertEquals(20, crossings(result).getLast().timeInSecSinceLastTriggerPoint, 0.002);
    }

    @Test
    void noisyConfirmedDwellMakesBothAdjacentTimingsAmbiguous() {
        var points = List.of(point(-30, 0, 0d), point(-1, 1.5, 10d), point(1, 1.5, 20d),
                point(-1, 0.1, 30d), point(1, 0.1, 40d), point(-1, 1.5, 50d), point(1, 1.5, 60d),
                point(-1, 1.5, 70d), point(30, 0, 80d), point(100, 0, 90d));
        for (int i = 2; i <= 7; i++) points.get(i).setMotionState(TrackMotionAnalyzer.State.STOPPED);
        var result = run(points, List.of(trigger("Before", -30, 0), trigger("A", 0, 0), trigger("B", 100, 0)), 10, List.of());
        assertEquals(List.of("Before", "A", "B"), names(result));
        for (var crossing : crossings(result).subList(1, 3)) {
            assertEquals(SegmentTimingStatus.AMBIGUOUS_DWELL, crossing.timingStatusSinceLastTriggerPoint);
            assertNull(crossing.timeInSecSinceLastTriggerPoint);
            assertNull(crossing.avgSpeedSinceLastTriggerPoint);
            assertNotNull(crossing.distanceInMeterSinceLastTriggerPoint);
        }
        assertTrue(result.segmentsStats.isEmpty());
    }

    @Test
    void confirmedIdenticalDwellMakesBothAdjacentTimingsAmbiguousAndRetainsStopNotes() {
        var points = List.of(point(0, 0, 0d), point(50, 0, 5d), point(50, 0, 35d),
                point(50, 0, 65d), point(100, 0, 70d));
        points.get(2).setMotionState(TrackMotionAnalyzer.State.STOPPED);
        points.get(3).setMotionState(TrackMotionAnalyzer.State.STOPPED);
        var result = run(points, List.of(trigger("Before", 0, 0), trigger("A", 50, 0), trigger("B", 100, 0)),
                2, List.of(stop(5, 65)));
        assertEquals(List.of("Before", "A", "B"), names(result));
        for (var crossing : crossings(result).subList(1, 3)) {
            assertEquals(SegmentTimingStatus.AMBIGUOUS_DWELL, crossing.timingStatusSinceLastTriggerPoint);
            assertNull(crossing.timeInSecSinceLastTriggerPoint);
            assertNull(crossing.avgSpeedSinceLastTriggerPoint);
            assertNotNull(crossing.distanceInMeterSinceLastTriggerPoint);
        }
        var end = crossings(result).getLast();
        assertEquals(SegmentTimingStatus.AMBIGUOUS_DWELL, end.timingStatusSinceLastTriggerPoint);
        assertNull(end.timeInSecSinceLastTriggerPoint);
        assertNull(end.avgSpeedSinceLastTriggerPoint);
        assertNotNull(end.distanceInMeterSinceLastTriggerPoint);
        assertTrue(result.segmentsStats.isEmpty());
        assertEquals(60, end.segmentNotesSinceLastTriggerPoint.totalStoppedSec, 0.002);
        assertEquals(1, end.segmentNotesSinceLastTriggerPoint.stopCount);
        assertFalse(end.segmentNotesSinceLastTriggerPoint.coverageComplete);
    }

    @Test
    void overnightEndpointElapsedIsRetainedButExcludedFromStatistics() {
        var result = run(List.of(point(0, 0, 0d), point(50, 0, 5d), point(60, 0, 72000d),
                        point(100, 0, 72004d), point(0, 0, 90000d), point(100, 0, 90010d)),
                List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 2, List.of());
        var overnight = crossings(result).get(1);
        assertEquals(72004d, overnight.timeInSecSinceLastTriggerPoint);
        assertTrue(overnight.recordingGapSinceLastTriggerPoint);
        var ab = result.segmentsStats.stream().filter(segment -> segment.point1.equals("A") && segment.point2.equals("B"))
                .findFirst().orElseThrow();
        assertEquals(1, ab.count);
        assertEquals(10d, ab.timeDelta);
    }

    @Test
    void supportedStopsRemainObservedLowerBoundsAcrossARecordingGap() {
        var points = List.of(point(0, 0, 0d), point(50, 0, 5d), point(50, 0, 35d), point(50, 0, 65d),
                point(60, 0, 3600d), point(100, 0, 3604d));
        points.get(2).setMotionState(TrackMotionAnalyzer.State.STOPPED);
        points.get(3).setMotionState(TrackMotionAnalyzer.State.STOPPED);
        var result = run(points, List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 2, List.of());
        var end = crossings(result).getLast();
        assertEquals(3604d, end.timeInSecSinceLastTriggerPoint);
        assertEquals(60d, end.segmentNotesSinceLastTriggerPoint.totalStoppedSec);
        assertEquals(1, end.segmentNotesSinceLastTriggerPoint.stopCount);
        assertFalse(end.segmentNotesSinceLastTriggerPoint.coverageComplete);
        assertTrue(result.segmentsStats.isEmpty());
    }

    @Test
    void realisticEdgeScatterRemainsOneVisitWithinTheOuterCircle() {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int angle = 180; angle >= 0; angle -= 5) {
            double radius = 20 + new double[]{-2, 3, -1, 2}[angle / 5 % 4];
            points.add(point(radius * Math.cos(Math.toRadians(angle)), radius * Math.sin(Math.toRadians(angle)),
                    (180 - angle) / 5d));
        }
        points.add(point(300, 0, 60d));
        var result = run(points, List.of(trigger("A", 0, 0), trigger("B", 300, 0)), 20, List.of());
        assertEquals(List.of("A", "B"), names(result));
    }

    @Test
    void proportionalExitMarginAndTwoMetreMinimumStillAllowTrueReturns() {
        for (double[] radii : List.of(new double[]{100, 115, 125}, new double[]{1, 2.5, 3.5})) {
            var result = run(List.of(point(0, 0, 0d), point(radii[1], 0, 10d), point(0, 0, 20d),
                            point(radii[2], 0, 30d), point(0, 0, 40d)),
                    List.of(trigger("A", 0, 0)), radii[0], List.of());
            assertEquals(List.of("A", "A"), names(result));
            assertEquals(40d, crossings(result).getLast().timeInSecSinceLastTriggerPoint);
        }
    }

    @Test
    void repeatedTraversalStatisticsRemainTotalsRatherThanMeans() {
        var result = run(List.of(point(0, 0, 0d), point(100, 0, 10d), point(0, 0, 20d), point(100, 0, 40d)),
                List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 2, List.of());
        var ab = result.segmentsStats.stream().filter(s -> s.point1.equals("A") && s.point2.equals("B")).findFirst().orElseThrow();
        assertEquals(2, ab.count);
        assertEquals(30, ab.timeDelta, 0.002);
    }

    @Test
    void manyLapMeasurementsDoNotRescanTheFullSourceForEveryVisit() {
        List<GpsTrackDataPoint> source = new ArrayList<>();
        for (int i = 0; i <= 800; i++) source.add(point(i % 2 == 0 ? 0 : 100, 0, i * 10d));
        var points = new AbstractList<GpsTrackDataPoint>() {
            int reads;
            @Override public GpsTrackDataPoint get(int index) { reads++; return source.get(index); }
            @Override public int size() { return source.size(); }
        };
        var result = run(points, List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 2, List.of());
        assertEquals(801, crossings(result).size());
        assertEquals(400, result.segmentsStats.getFirst().count);
        assertEquals(4000, result.segmentsStats.getFirst().timeDelta, 0.002);
        assertTrue(points.reads < source.size() * 100,
                "Source accesses must grow with points and visits, not their product: " + points.reads);
    }

    @Test
    void candidatesOutsideRouteIntersectionUseOnlyGeometryQualificationAndExactCounts() {
        var tracks = mock(GpsTrackRepository.class);
        var dataPoints = mock(GpsTrackDataPointRepository.class);
        var events = mock(GpsTrackEventRepository.class);
        var variants = mock(GpsTrackVariantSelector.class);
        var a = trigger("A", 100, 10.1);
        var b = trigger("B", 200, 0);
        when(tracks.getSegmentCandidateTrackIds(eq(a.coordinate.x), eq(a.coordinate.y), eq(10d), any()))
                .thenReturn(List.of(1L));
        when(tracks.getSegmentCandidateTrackIds(eq(b.coordinate.x), eq(b.coordinate.y), eq(10d), any()))
                .thenReturn(List.of(2L));
        var sizes = List.of(qualificationSize(1, 2), qualificationSize(2, 1));
        when(dataPoints.findSegmentQualificationSizes(any(), any())).thenReturn(sizes);
        var qualificationPoints = List.of(
                qualificationPoint(1L, 0, point(0, 0, 0d)), qualificationPoint(1L, 1, point(400, 0, 40d)),
                qualificationPoint(2L, 0, point(200, 0, 0d)));
        when(dataPoints.findSegmentQualificationPoints(any(), any())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(0);
            return qualificationPoints.stream().filter(row -> ids.contains(row.getTrackId())).toList();
        });
        var request = new CrossingPointsRequest();
        request.radius = 10d;
        request.triggerPoints = List.of(a, b);
        var result = new TrackTimeBetweenTwoPoints(tracks, dataPoints, events, variants, directReads())
                .getTrackCrossingsWithoutSummaries(request, new Long[]{1L, 2L});
        assertEquals(Map.of("A", 0, "B", 1), result.tracksPerZone);
        assertTrue(result.crossings.isEmpty());
        org.mockito.Mockito.verifyNoInteractions(variants);
        org.mockito.Mockito.verify(tracks, org.mockito.Mockito.never()).findById(any());
        org.mockito.Mockito.verify(dataPoints, org.mockito.Mockito.never()).findAllByGpsTrackDataIdOrderByPointIndexAsc(any());
    }

    @Test
    void candidateQualificationBatchesByPointsAndIsolatesDenseTracks() {
        var tracks = mock(GpsTrackRepository.class);
        var dataPoints = mock(GpsTrackDataPointRepository.class);
        var events = mock(GpsTrackEventRepository.class);
        var variants = mock(GpsTrackVariantSelector.class);
        var a = trigger("A", 0, 0);
        var b = trigger("B", 100, 0);
        when(tracks.getSegmentCandidateTrackIds(eq(a.coordinate.x), eq(a.coordinate.y), eq(1d), any()))
                .thenReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L));
        when(tracks.getSegmentCandidateTrackIds(eq(b.coordinate.x), eq(b.coordinate.y), eq(1d), any()))
                .thenReturn(List.of());
        // Track 6 has no stored points and therefore no size row or qualifying visit.
        var sizes = List.of(qualificationSize(1, 12_000), qualificationSize(2, 8_000), qualificationSize(3, 20_001),
                qualificationSize(4, 15_000), qualificationSize(5, 6_000));
        when(dataPoints.findSegmentQualificationSizes(any(), any())).thenReturn(sizes);
        List<List<Long>> batches = new ArrayList<>();
        when(dataPoints.findSegmentQualificationPoints(any(), any())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(0);
            batches.add(List.copyOf(ids));
            return ids.stream().map(id -> qualificationPoint(id, 0, point(0, 0, 0d))).toList();
        });
        var request = new CrossingPointsRequest();
        request.radius = 1d;
        request.triggerPoints = List.of(a, b);

        var result = new TrackTimeBetweenTwoPoints(tracks, dataPoints, events, variants, directReads())
                .getTrackCrossingsWithoutSummaries(request, null);

        assertEquals(List.of(List.of(1L, 2L), List.of(3L), List.of(4L), List.of(5L)), batches);
        assertEquals(Map.of("A", 5, "B", 0), result.tracksPerZone);
        assertTrue(result.crossings.isEmpty());
        org.mockito.Mockito.verifyNoInteractions(variants);
    }

    private static GpsTrackDataPointRepository.SegmentQualificationSize qualificationSize(long id, long points) {
        var row = mock(GpsTrackDataPointRepository.SegmentQualificationSize.class);
        when(row.getTrackId()).thenReturn(id);
        when(row.getPointCount()).thenReturn(points);
        return row;
    }

    @Test
    void publishesSourceDependentSummariesBeforeLoadingTheNextTrack() {
        var tracks = mock(GpsTrackRepository.class);
        var dataPoints = mock(GpsTrackDataPointRepository.class);
        var events = mock(GpsTrackEventRepository.class);
        var variants = mock(GpsTrackVariantSelector.class);
        List<String> order = new ArrayList<>();
        when(tracks.getSegmentCandidateTrackIds(anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(1L, 2L, 3L));
        when(tracks.findById(any())).thenAnswer(call -> {
            var track = new GpsTrack();
            track.setId(call.getArgument(0));
            return Optional.of(track);
        });
        when(variants.forMetricsId(any())).thenAnswer(call -> call.getArgument(0));
        when(dataPoints.findAllByGpsTrackDataIdOrderByPointIndexAsc(any())).thenAnswer(call -> {
            order.add("load-" + call.getArgument(0));
            var start = point(0, 0, 0d);
            var end = point(100, 0, 10d);
            start.setPointIndex(0);
            end.setPointIndex(1);
            return List.of(start, end);
        });
        var request = new CrossingPointsRequest();
        request.radius = 1d;
        request.triggerPoints = List.of(trigger("A", 0, 0), trigger("B", 100, 0));
        var response = new TrackTimeBetweenTwoPoints(tracks, dataPoints, events, variants, directReads())
                .getTrackTimeBetweenPoints(request, null, (track, points, trackEvents) -> {
                    com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentAttemptResolver.prepare(track, points, trackEvents);
                    order.add("summarize-" + track.gpsTrack.getId());
                });
        assertEquals(List.of("load-1", "summarize-1", "load-2", "summarize-2", "load-3", "summarize-3"), order);
        assertEquals(3, response.crossings.size());
        for (var track : response.crossings.values()) {
            assertEquals(1, track.getAttemptSummaries().size());
            assertEquals(10d, track.getAttemptSummaries().getFirst().elapsedSeconds());
            assertEquals(List.of(1, 1), track.getCrossings().stream().map(c -> c.visitNumber).toList());
        }
    }

    private static GpsTrackDataPointRepository.SegmentQualificationPoint qualificationPoint(long trackId, int index,
                                                                                            GpsTrackDataPoint point) {
        var row = mock(GpsTrackDataPointRepository.SegmentQualificationPoint.class);
        when(row.getTrackId()).thenReturn(trackId);
        when(row.getPointIndex()).thenReturn(index);
        when(row.getPointLongLat()).thenReturn(point.getPointLongLat());
        when(row.getPointTimestamp()).thenReturn(point.getPointTimestamp());
        return row;
    }

    @Test
    void actualVisitsDefineRoutePopulationAndIndependentZoneCounts() {
        var result = run(List.of(point(0, 0, 0d), point(400, 0, 40d)),
                List.of(trigger("A", 100, 10.1), trigger("B", 200, 0), trigger("C", 300, 0)), 10, List.of());
        assertTrue(result.crossings.isEmpty());
        assertTrue(result.segmentsStats.isEmpty());
        assertEquals(Map.of("A", 0, "B", 1, "C", 1), result.tracksPerZone);
    }

    @Test
    void anUntimedFixCannotHideAnHourGapButObservedEndpointElapsedRemainsAvailable() {
        var result = run(List.of(point(0, 0, 0d), point(20, 0, null), point(40, 0, 3600d)),
                List.of(trigger("A", 0, 0), trigger("B", 40, 0)), 1, List.of());
        var end = crossings(result).getLast();
        assertTrue(end.recordingGapSinceLastTriggerPoint);
        assertEquals(SegmentTimingStatus.AVAILABLE, end.timingStatusSinceLastTriggerPoint);
        assertEquals(3600d, end.timeInSecSinceLastTriggerPoint);
        assertNull(end.distanceInMeterSinceLastTriggerPoint);
        assertNull(end.avgSpeedSinceLastTriggerPoint);
        var interior = run(List.of(point(0, 0, 0d), point(20, 0, null), point(40, 0, 3600d)),
                List.of(trigger("A", 30, 0)), 1, List.of());
        assertTrue(interior.crossings.isEmpty());
    }

    @Test
    void aClockResetInsideTheAttemptInvalidatesEvenPositiveEndpointDifference() {
        var result = run(List.of(point(0, 0, 0d), point(10, 0, 20d), point(20, 0, 5d), point(30, 0, 30d)),
                List.of(trigger("A", 0, 0), trigger("B", 30, 0)), 1, List.of());
        assertEquals(SegmentTimingStatus.INVALID_TIME, crossings(result).getLast().timingStatusSinceLastTriggerPoint);
        assertNull(crossings(result).getLast().timeInSecSinceLastTriggerPoint);
    }

    @Test
    void stoppedCoverageDoesNotLeakAcrossSectionsWithRepeatedTimes() {
        var points = List.of(point(0, 0, 0d), point(50, 0, 10d), point(50, 0, 40d), point(50, 0, 80d),
                point(100, 0, 90d), point(0, 0, 0d), point(100, 0, 90d));
        points.get(2).setMotionState(com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.STOPPED);
        points.get(3).setMotionState(com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State.STOPPED);
        var result = run(points, List.of(trigger("A", 0, 0), trigger("B", 100, 0)), 1, List.of());
        assertEquals(List.of("A", "B", "A", "B"), names(result));
        assertEquals(70, crossings(result).get(1).segmentNotesSinceLastTriggerPoint.totalStoppedSec);
        assertEquals(1, crossings(result).get(1).segmentNotesSinceLastTriggerPoint.stopCount);
        assertEquals(0, crossings(result).getLast().segmentNotesSinceLastTriggerPoint.totalStoppedSec);
        assertEquals(0, crossings(result).getLast().segmentNotesSinceLastTriggerPoint.stopCount);
    }

    private static GpsTrackDataPoint persistedPoint(Coordinate coordinate, int index) throws Exception {
        Method method = GPXStoreService.class.getDeclaredMethod("createGpsTrackDataPoint",
                Long.class, int.class, int.class, int.class, Coordinate.class, GeometryFactory.class, GPXReader.class);
        method.setAccessible(true);
        return (GpsTrackDataPoint) method.invoke(null, DATA_ID, 90, index, index,
                coordinate, FACTORY, new GPXReader());
    }

    private static GpsTrackDataPoint point(double x, double y, Double seconds) {
        return geographicPoint(ORIGIN_LON + x / METERS_PER_LON, ORIGIN_LAT + y / METERS_PER_LAT, seconds);
    }

    private static GpsTrackDataPoint geographicPoint(double lon, double lat, Double seconds) {
        var point = new GpsTrackDataPoint();
        point.setPointLongLat(FACTORY.createPoint(new Coordinate(lon, lat)));
        point.setPointTimestamp(seconds == null ? null : new Date(BASE_MS + Math.round(seconds * 1000)));
        point.setDurationSinceStart(seconds);
        return point;
    }

    private static TriggerPoint trigger(String name, double x, double y) {
        return new TriggerPoint(name, new Coordinate(ORIGIN_LON + x / METERS_PER_LON, ORIGIN_LAT + y / METERS_PER_LAT));
    }

    private static GpsTrackEvent stop(double start, double end) {
        var event = new GpsTrackEvent();
        event.setEventType(GpsTrackEvent.EVENT_TYPE.STOP);
        event.setSource(GpsTrackEvent.SOURCE.DETECTED);
        event.setStartTimestamp(new Date(BASE_MS + Math.round(start * 1000)));
        event.setEndTimestamp(new Date(BASE_MS + Math.round(end * 1000)));
        return event;
    }

    private static double geodesic(GpsTrackDataPoint a, GpsTrackDataPoint b) {
        return Geodesic.WGS84.Inverse(a.getPointLongLat().getY(), a.getPointLongLat().getX(),
                b.getPointLongLat().getY(), b.getPointLongLat().getX()).s12;
    }

    private static double pathDistance(List<GpsTrackDataPoint> points) {
        double distance = 0;
        for (int i = 1; i < points.size(); i++) distance += geodesic(points.get(i - 1), points.get(i));
        return distance;
    }

    private static List<Crossing> crossings(CrossingPointsResponse result) {
        return result.crossings.containsKey(TRACK_ID) ? result.crossings.get(TRACK_ID).getCrossings() : List.of();
    }

    private static List<String> names(CrossingPointsResponse result) {
        return crossings(result).stream().map(c -> c.triggerPoint.name).toList();
    }

    private static CrossingPointsResponse run(List<GpsTrackDataPoint> points, List<TriggerPoint> triggers,
                                              double radius, List<GpsTrackEvent> stops) {
        return run(points, triggers, radius, stops, false);
    }

    private static CrossingPointsResponse run(List<GpsTrackDataPoint> points, List<TriggerPoint> triggers,
                                              double radius, List<GpsTrackEvent> stops, boolean unreliable) {
        var tracks = mock(GpsTrackRepository.class);
        var dataPoints = mock(GpsTrackDataPointRepository.class);
        var events = mock(GpsTrackEventRepository.class);
        var variants = mock(GpsTrackVariantSelector.class);
        when(tracks.getSegmentCandidateTrackIds(anyDouble(), anyDouble(), eq(radius), any()))
                .thenReturn(List.of(TRACK_ID));
        GpsTrack track = new GpsTrack();
        track.setId(TRACK_ID);
        track.setTimingUnreliable(unreliable);
        when(tracks.findById(TRACK_ID)).thenReturn(Optional.of(track));
        when(variants.forMetricsId(TRACK_ID)).thenReturn(DATA_ID);
        double distance = 0;
        for (int i = 0; i < points.size(); i++) {
            var point = points.get(i);
            point.setId(100L + i);
            point.setGpsTrackDataId(DATA_ID);
            point.setPointIndex(i);
            point.setPointIndexMax(points.size() - 1);
            if (i > 0) distance += geodesic(points.get(i - 1), point);
            point.setDistanceInMeterSinceStart(distance);
        }
        when(dataPoints.findAllByGpsTrackDataIdOrderByPointIndexAsc(DATA_ID)).thenReturn(points);
        when(events.findAllByGpsTrackIdOrderByStartPointIndexAsc(TRACK_ID)).thenReturn(stops);
        var request = new CrossingPointsRequest();
        request.radius = radius;
        request.triggerPoints = triggers;
        return new TrackTimeBetweenTwoPoints(tracks, dataPoints, events, variants, directReads())
                .getTrackCrossingsWithoutSummaries(request, new Long[]{TRACK_ID});
    }
}
