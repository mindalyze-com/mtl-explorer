package com.x8ing.mtl.server.mtlserver.logic.crossing;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsRequest;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsResponse;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.TriggerPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Visualization-based test for TrackTimeBetweenTwoPoints crossing detection.
 * <p>
 * This test uses a simple rectangular coordinate system (0-100 meters) for clarity,
 * then converts to WGS84 coordinates for the actual test execution.
 */
class TrackTimeBetweenTwoPointsVisualizationTest {

    @Mock
    private GpsTrackRepository gpsTrackRepository;

    @Mock
    private GpsTrackVariantSelector gpsTrackVariantSelector;

    @Mock
    private GpsTrackDataPointRepository gpsTrackDataPointRepository;

    private TrackTimeBetweenTwoPoints service;

    private final GeometryFactory factory = new GeometryFactory();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TrackTimeBetweenTwoPoints(
                gpsTrackRepository,
                gpsTrackDataPointRepository,
                gpsTrackVariantSelector
        );
    }

    @Test
    void testGetTrackTimeBetweenPoints_VisualPath() {
        /*
         * ===== VISUALIZATION (Meter Coordinate System) =====
         *
         * Simple rectangular grid: 0-100 meters on both X and Y axes
         *
         * Y (meters)
         * 60 |
         *    |
         * 50 |     P1          <- GPS point at (50, 50)
         *    |    /  \
         * 40 |   ([A])  ([B])  <- Trigger points at y=40m, radius=12m
         *    |  / :    \ :     <- Circles now intersect the V-shaped path
         * 30 | /  :     \:
         *    |/   X      X\    <- X marks where track crosses trigger zones
         * 20 |    :     / :
         *    |    :    /  :
         * 10 |    :   /   :
         *    |    :  /    :
         *  0 P0---+------P2--- X (meters)
         *    0   25     75 100
         *
         * Track path (V-shaped, equation y=x then y=-x+100):
         *   P0: (0m, 0m)     at t=0s,  distance=0m
         *   P1: (50m, 50m)   at t=5s,  distance=70.7m  (diagonal up-right)
         *   P2: (100m, 0m)   at t=10s, distance=141.4m (diagonal down-right)
         *
         * Trigger points:
         *   A: (25m, 40m) radius=12m
         *      - Distance from center to line y=x: |40-25|/√2 ≈ 10.6m
         *      - Circle intersects at two points on segment P0->P1
         *   B: (75m, 40m) radius=12m
         *      - Distance from center to line y=-x+100: |40-25|/√2 ≈ 10.6m
         *      - Circle intersects at two points on segment P1->P2
         *
         * Key insight: Both trigger points have their centers OUTSIDE the track,
         * but the track segments pass THROUGH both circles (radius > min distance).
         * This tests the mid-segment intersection detection.
         */

        // ===== STEP 1: DEFINE GEOMETRY IN METERS (EASY TO VISUALIZE) =====

        double[][] pathMeters = {
                {0.0, 0.0},      // P0: Start at origin
                {50.0, 50.0},    // P1: Peak of the V
                {100.0, 0.0}     // P2: End at x=100
        };

        double[][] triggerMeters = {
                {25.0, 40.0},    // Trigger A: left side
                {75.0, 40.0}     // Trigger B: right side
        };

        // Radius must be > 10.6m (distance from trigger centers to the path)
        // Use 12m to ensure clear intersection
        double radiusMeters = 12.0;

        // ===== STEP 2: CONVERT TO WGS84 COORDINATES =====

        // Use equator as reference point for simplest conversion
        double baseLat = 0.0;
        double baseLon = 0.0;

        // Conversion factors at equator (approximate):
        // 1 degree longitude ≈ 111,320 meters
        // 1 degree latitude ≈ 110,540 meters
        double metersPerDegreeLon = 111320.0;
        double metersPerDegreeLat = 110540.0;

        // Convert path points to WGS84
        List<GpsTrackDataPoint> trackDataPoints = new ArrayList<>();
        long startTime = 1000000000000L; // Jan 1, 2001

        for (int i = 0; i < pathMeters.length; i++) {
            GpsTrackDataPoint point = new GpsTrackDataPoint();
            point.setPointIndex(i);

            // Convert meters to degrees
            double lon = baseLon + pathMeters[i][0] / metersPerDegreeLon;
            double lat = baseLat + pathMeters[i][1] / metersPerDegreeLat;

            Coordinate coord = new Coordinate(lon, lat, 0.0); // No altitude
            Point jtsPoint = factory.createPoint(coord);
            point.setPointLongLat(jtsPoint);

            // Time: 5 seconds per segment
            point.setPointTimestamp(new Date(startTime + i * 5000L));

            // Distance: calculate actual Euclidean distance in meters
            double distance = 0.0;
            if (i > 0) {
                double dx = pathMeters[i][0] - pathMeters[i - 1][0];
                double dy = pathMeters[i][1] - pathMeters[i - 1][1];
                double segmentDistance = Math.sqrt(dx * dx + dy * dy);
                distance = trackDataPoints.get(i - 1).getDistanceInMeterSinceStart() + segmentDistance;
            }
            point.setDistanceInMeterSinceStart(distance);

            trackDataPoints.add(point);
        }

        System.out.println("\n===== GPS TRACK POINTS =====");
        for (int i = 0; i < trackDataPoints.size(); i++) {
            GpsTrackDataPoint p = trackDataPoints.get(i);
            System.out.printf("P%d: (%.6f, %.6f) at t=%ds, distance=%.1fm%n",
                    i,
                    p.getPointLongLat().getX(),
                    p.getPointLongLat().getY(),
                    (p.getPointTimestamp().getTime() - startTime) / 1000,
                    p.getDistanceInMeterSinceStart()
            );
        }

        // Convert triggers to WGS84
        TriggerPoint triggerA = new TriggerPoint();
        triggerA.name = "A";
        triggerA.coordinate = new Coordinate(
                baseLon + triggerMeters[0][0] / metersPerDegreeLon,
                baseLat + triggerMeters[0][1] / metersPerDegreeLat,
                0
        );

        TriggerPoint triggerB = new TriggerPoint();
        triggerB.name = "B";
        triggerB.coordinate = new Coordinate(
                baseLon + triggerMeters[1][0] / metersPerDegreeLon,
                baseLat + triggerMeters[1][1] / metersPerDegreeLat,
                0
        );

        System.out.println("\n===== TRIGGER POINTS =====");
        System.out.printf("A: (%.6f, %.6f) radius=%.1fm%n",
                triggerA.coordinate.x, triggerA.coordinate.y, radiusMeters);
        System.out.printf("B: (%.6f, %.6f) radius=%.1fm%n",
                triggerB.coordinate.x, triggerB.coordinate.y, radiusMeters);

        CrossingPointsRequest request = new CrossingPointsRequest();
        request.setTriggerPoints(List.of(triggerA, triggerB));
        request.setRadius(radiusMeters);

        // ===== STEP 3: MOCK REPOSITORY RESPONSES =====

        Long trackId = 1L;
        Long trackDataId = 10L;

        GpsTrack track = new GpsTrack();
        track.setId(trackId);
        track.setTrackName("V-Shaped Test Track");

        // Mock that both triggers find the same track
        when(gpsTrackRepository.getTracksWithinDistanceToPoint(
                eq(triggerA.coordinate.x),
                eq(triggerA.coordinate.y),
                eq(radiusMeters),
                any()
        )).thenReturn(List.of(trackId));

        when(gpsTrackRepository.getTracksWithinDistanceToPoint(
                eq(triggerB.coordinate.x),
                eq(triggerB.coordinate.y),
                eq(radiusMeters),
                any()
        )).thenReturn(List.of(trackId));

        when(gpsTrackRepository.findById(trackId)).thenReturn(Optional.of(track));

        when(gpsTrackVariantSelector.forMetricsId(trackId)).thenReturn(trackDataId);

        when(gpsTrackDataPointRepository.findAllByGpsTrackDataIdOrderByPointIndexAsc(trackDataId))
                .thenReturn(trackDataPoints);

        // ===== STEP 4: EXECUTE =====

        System.out.println("\n===== EXECUTING TEST =====");
        CrossingPointsResponse response = service.getTrackTimeBetweenPoints(request, null);

        // ===== STEP 5: ASSERTIONS =====

        System.out.println("\n===== RESULTS =====");
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getCrossings(), "Crossings map should not be null");

        var trackCrossings = response.getCrossings().get(trackId);
        assertNotNull(trackCrossings, "Should have crossings for track " + trackId);

        var crossings = trackCrossings.getCrossings();
        assertNotNull(crossings, "Crossings list should not be null");

        System.out.printf("Found %d crossings%n", crossings.size());

        for (int i = 0; i < crossings.size(); i++) {
            var crossing = crossings.get(i);
            System.out.printf("Crossing %d: Trigger '%s' at (%.6f, %.6f), t=%.2fs, dist=%.1fm%n",
                    i + 1,
                    crossing.triggerPoint.name,
                    crossing.gpsTrackDataPoint.getPointLongLat().getX(),
                    crossing.gpsTrackDataPoint.getPointLongLat().getY(),
                    (crossing.gpsTrackDataPoint.getPointTimestamp().getTime() - startTime) / 1000.0,
                    crossing.gpsTrackDataPoint.getDistanceInMeterSinceStart()
            );
        }

        // Should detect 2 crossings: one for each trigger
        assertEquals(2, crossings.size(),
                "Should detect exactly 2 crossings (one per trigger). " +
                "The V-shaped path crosses through both trigger circles.");

        // Verify both triggers were crossed
        var triggerNames = crossings.stream()
                .map(c -> c.triggerPoint.name)
                .toList();
        assertTrue(triggerNames.contains("A"), "Should have crossing for trigger A");
        assertTrue(triggerNames.contains("B"), "Should have crossing for trigger B");

        // Verify crossings are interpolated (not at exact GPS points)
        for (var crossing : crossings) {
            assertNotNull(crossing.gpsTrackDataPoint.getPointTimestamp(),
                    "Crossing should have timestamp");
            assertNotNull(crossing.gpsTrackDataPoint.getDistanceInMeterSinceStart(),
                    "Crossing should have distance");

            // Crossing should be interpolated (between t=0 and t=10)
            long timeMs = crossing.gpsTrackDataPoint.getPointTimestamp().getTime();
            assertTrue(timeMs > startTime && timeMs < startTime + 10000,
                    "Crossing time should be interpolated between start and end");
        }

        System.out.println("\n✅ TEST PASSED - Mid-segment intersections detected correctly!");

        // Verify repository interactions
        verify(gpsTrackRepository, times(1)).getTracksWithinDistanceToPoint(
                eq(triggerA.coordinate.x), eq(triggerA.coordinate.y), eq(radiusMeters), any());
        verify(gpsTrackRepository, times(1)).getTracksWithinDistanceToPoint(
                eq(triggerB.coordinate.x), eq(triggerB.coordinate.y), eq(radiusMeters), any());
        verify(gpsTrackRepository, times(1)).findById(trackId);
        verify(gpsTrackVariantSelector, times(1)).forMetricsId(trackId);
        verify(gpsTrackDataPointRepository, times(1))
                .findAllByGpsTrackDataIdOrderByPointIndexAsc(trackDataId);
    }

    /**
     * Track that passes nowhere near the trigger — repository returns no matching tracks,
     * so the response should contain no crossings.
     */
    @Test
    void testGetTrackTimeBetweenPoints_NoCrossing() {
        TriggerPoint triggerA = new TriggerPoint();
        triggerA.name = "A";
        triggerA.coordinate = new Coordinate(0.0, 0.0, 0.0);

        CrossingPointsRequest request = new CrossingPointsRequest();
        request.setTriggerPoints(List.of(triggerA));
        request.setRadius(10.0);

        when(gpsTrackRepository.getTracksWithinDistanceToPoint(
                eq(triggerA.coordinate.x), eq(triggerA.coordinate.y), eq(10.0), any()))
                .thenReturn(List.of());

        CrossingPointsResponse response = service.getTrackTimeBetweenPoints(request, null);
        assertNotNull(response);
        assertTrue(response.getCrossings() == null || response.getCrossings().isEmpty(),
                "No crossings expected when no tracks are near the trigger");
    }

    /**
     * Track points without altitude (Z = NaN in JTS) — interpolation must not produce NaN
     * in the resulting crossing point coordinates.
     */
    @Test
    void testGetTrackTimeBetweenPoints_NoAltitude_NaNZNotPropagated() {
        double metersPerDegreeLon = 111320.0;
        double metersPerDegreeLat = 110540.0;

        // Straight track along the x-axis (y = 0) from x=0 to x=100 m
        double[][] pathMeters = {{0.0, 0.0}, {50.0, 0.0}, {100.0, 0.0}};
        List<GpsTrackDataPoint> trackDataPoints = new ArrayList<>();
        long startTime = 2000000000000L;

        for (int i = 0; i < pathMeters.length; i++) {
            GpsTrackDataPoint p = new GpsTrackDataPoint();
            p.setPointIndex(i);
            double lon = pathMeters[i][0] / metersPerDegreeLon;
            double lat = pathMeters[i][1] / metersPerDegreeLat;
            // Deliberately omit Z — JTS Coordinate defaults to Double.NaN
            p.setPointLongLat(factory.createPoint(new Coordinate(lon, lat)));
            p.setPointTimestamp(new Date(startTime + i * 5000L));
            p.setDistanceInMeterSinceStart((double) i * 50);
            trackDataPoints.add(p);
        }

        // Trigger sits 8 m off the track mid-point; radius 10 m → track passes through
        TriggerPoint trigger = new TriggerPoint();
        trigger.name = "C";
        trigger.coordinate = new Coordinate(50.0 / metersPerDegreeLon, 8.0 / metersPerDegreeLat, 0.0);
        double radius = 10.0;

        CrossingPointsRequest request = new CrossingPointsRequest();
        request.setTriggerPoints(List.of(trigger));
        request.setRadius(radius);

        Long trackId = 3L;
        Long trackDataId = 30L;
        GpsTrack track = new GpsTrack();
        track.setId(trackId);

        when(gpsTrackRepository.getTracksWithinDistanceToPoint(
                eq(trigger.coordinate.x), eq(trigger.coordinate.y), eq(radius), any()))
                .thenReturn(List.of(trackId));
        when(gpsTrackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(gpsTrackVariantSelector.forMetricsId(trackId)).thenReturn(trackDataId);
        when(gpsTrackDataPointRepository.findAllByGpsTrackDataIdOrderByPointIndexAsc(trackDataId))
                .thenReturn(trackDataPoints);

        CrossingPointsResponse response = service.getTrackTimeBetweenPoints(request, null);
        assertNotNull(response);

        var trackCrossings = response.getCrossings().get(trackId);
        assertNotNull(trackCrossings, "Should have crossings for track " + trackId);
        assertFalse(trackCrossings.getCrossings().isEmpty(), "Expected at least one crossing");

        for (var crossing : trackCrossings.getCrossings()) {
            double lon = crossing.gpsTrackDataPoint.getPointLongLat().getX();
            double lat = crossing.gpsTrackDataPoint.getPointLongLat().getY();
            double z = crossing.gpsTrackDataPoint.getPointLongLat().getCoordinate().z;
            assertFalse(Double.isNaN(lon), "Interpolated lon must not be NaN");
            assertFalse(Double.isNaN(lat), "Interpolated lat must not be NaN");
            assertFalse(Double.isNaN(z), "Interpolated altitude must not be NaN");
        }
    }

    /**
     * Track points with no timestamps — interpolation must not throw NPE, and
     * crossing timestamps should be null (not silently defaulted).
     */
    @Test
    void testGetTrackTimeBetweenPoints_NoTimestamps_NoNPE() {
        double metersPerDegreeLon = 111320.0;

        double[][] pathMeters = {{0.0, 0.0}, {50.0, 0.0}, {100.0, 0.0}};
        List<GpsTrackDataPoint> trackDataPoints = new ArrayList<>();

        for (int i = 0; i < pathMeters.length; i++) {
            GpsTrackDataPoint p = new GpsTrackDataPoint();
            p.setPointIndex(i);
            double lon = pathMeters[i][0] / metersPerDegreeLon;
            p.setPointLongLat(factory.createPoint(new Coordinate(lon, 0.0, 0.0)));
            p.setPointTimestamp(null); // deliberately missing
            p.setDistanceInMeterSinceStart((double) i * 50);
            trackDataPoints.add(p);
        }

        TriggerPoint trigger = new TriggerPoint();
        trigger.name = "D";
        trigger.coordinate = new Coordinate(50.0 / metersPerDegreeLon, 8.0 / 110540.0, 0.0);
        double radius = 10.0;

        CrossingPointsRequest request = new CrossingPointsRequest();
        request.setTriggerPoints(List.of(trigger));
        request.setRadius(radius);

        Long trackId = 4L;
        Long trackDataId = 40L;
        GpsTrack track = new GpsTrack();
        track.setId(trackId);

        when(gpsTrackRepository.getTracksWithinDistanceToPoint(
                eq(trigger.coordinate.x), eq(trigger.coordinate.y), eq(radius), any()))
                .thenReturn(List.of(trackId));
        when(gpsTrackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(gpsTrackVariantSelector.forMetricsId(trackId)).thenReturn(trackDataId);
        when(gpsTrackDataPointRepository.findAllByGpsTrackDataIdOrderByPointIndexAsc(trackDataId))
                .thenReturn(trackDataPoints);

        CrossingPointsResponse response = assertDoesNotThrow(
                () -> service.getTrackTimeBetweenPoints(request, null),
                "Must not throw when track points have no timestamps");
        assertNotNull(response);

        var trackCrossings = response.getCrossings().get(trackId);
        if (trackCrossings != null) {
            for (var crossing : trackCrossings.getCrossings()) {
                assertNull(crossing.gpsTrackDataPoint.getPointTimestamp(),
                        "Crossing timestamp must be null when source points have no timestamps");
            }
        }
    }
}
