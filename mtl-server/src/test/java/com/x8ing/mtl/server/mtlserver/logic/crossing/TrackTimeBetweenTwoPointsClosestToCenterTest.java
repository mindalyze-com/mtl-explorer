package com.x8ing.mtl.server.mtlserver.logic.crossing;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.when;

/**
 * Conceptual correctness tests for {@link TrackTimeBetweenTwoPoints}.
 * <p>
 * These tests encode the essential semantic of the trigger-point crossing
 * detector: <b>each zone visit must be represented by the single track point
 * closest to the trigger's center</b> — not by the zone-entry edge.
 * <p>
 * Historically the detector recorded the entry point (first sample inside the
 * radius), which biased every downstream consumer (sector timing, distance,
 * race animation) by up to {@code radius} on each side. These tests fail on
 * the old edge-based implementation and pass on the closest-to-center one.
 * <p>
 * Geometry is defined in a small metric frame (metres) anchored at the equator
 * and converted to WGS84 so the haversine-based distance used inside the
 * service (via {@link GPXReader}) behaves as expected.
 */
class TrackTimeBetweenTwoPointsClosestToCenterTest {

    @Mock
    private GpsTrackRepository gpsTrackRepository;

    @Mock
    private GpsTrackVariantSelector gpsTrackVariantSelector;

    @Mock
    private GpsTrackDataPointRepository gpsTrackDataPointRepository;

    private TrackTimeBetweenTwoPoints service;

    private final GeometryFactory factory = new GeometryFactory();

    /**
     * 1 degree at the equator, roughly.
     */
    private static final double METERS_PER_DEG_LON = 111320.0;
    private static final double METERS_PER_DEG_LAT = 110540.0;

    /**
     * Tolerance used when asserting "closest to center" in metres.
     */
    private static final double CLOSEST_TOL_M = 2.0;

    private static final Long TRACK_ID = 1L;
    private static final Long TRACK_DATA_ID = 10L;
    private static final long START_TIME = 1_700_000_000_000L;
    /**
     * 1 metre per second — makes time assertions easy.
     */
    private static final double SPEED_MPS = 1.0;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TrackTimeBetweenTwoPoints(
                gpsTrackRepository,
                gpsTrackDataPointRepository,
                gpsTrackVariantSelector
        );
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    /**
     * A perfectly straight eastbound track passes directly through the centre
     * of trigger A. With a 20 m radius the track enters the zone at x=80 m
     * and leaves at x=120 m. The crossing <b>must</b> be recorded at the
     * centre (x≈100 m), not at the entry (x≈80 m).
     */
    @Test
    @DisplayName("Straight track through centre: crossing is at the centre, not at the zone entry")
    void crossingIsRecordedAtCenterForStraightThroughPath() {
        double[][] pathMeters = new double[][]{
                {0, 0}, {50, 0}, {100, 0}, {150, 0}, {200, 0}
        };
        TriggerPoint a = trigger("A", 100.0, 0.0);
        double radius = 20.0;

        List<Crossing> crossings = runSingleTrigger(pathMeters, a, radius);

        assertEquals(1, crossings.size(), "Straight pass through a single zone must produce exactly one crossing");
        Crossing c = crossings.get(0);

        double distToCenterM = distanceFromCrossingToTriggerMeters(c, a);
        assertTrue(distToCenterM <= CLOSEST_TOL_M,
                "Crossing should be at the centre (±" + CLOSEST_TOL_M + " m) but was " + distToCenterM + " m away. " +
                "This is the exact bug the old edge-based detector had: it recorded the entry point " +
                "which sits ~" + (int) radius + " m offset from the centre.");

        // The old bug would pick the entry at x=80m with t=80s. The correct
        // closest-to-center point is at x=100m with t=100s.
        long tSec = (c.gpsTrackDataPoint.getPointTimestamp().getTime() - START_TIME) / 1000L;
        assertTrue(Math.abs(tSec - 100) <= 2,
                "Crossing timestamp should correspond to the centre (~100 s) but was " + tSec + " s");
    }

    /**
     * Two triggers on a straight line. The reported <i>time between A and B</i>
     * must equal the centre-to-centre travel time. On the old edge-based
     * implementation this was under-reported by {@code 2 * radius / speed}.
     */
    @Test
    @DisplayName("Sector measurement A→B reflects centre-to-centre transit, not edge-to-edge")
    void sectorTimeMatchesCenterToCenterTransit() {
        // Straight eastbound track sampled every 10 m; A at x=200, B at x=500; speed = 1 m/s.
        double[][] pathMeters = new double[][]{
                {0, 0}, {50, 0}, {100, 0}, {150, 0}, {180, 0},
                {190, 0}, {200, 0}, {210, 0}, {220, 0},
                {260, 0}, {300, 0}, {350, 0}, {400, 0}, {450, 0},
                {480, 0}, {490, 0}, {500, 0}, {510, 0}, {520, 0},
                {560, 0}, {600, 0}
        };
        TriggerPoint a = trigger("A", 200.0, 0.0);
        TriggerPoint b = trigger("B", 500.0, 0.0);
        double radius = 20.0;

        List<Crossing> crossings = runTwoTriggers(pathMeters, a, b, radius);

        assertEquals(2, crossings.size(), "One straight pass should yield one crossing per trigger");

        Crossing cA = byName(crossings, "A");
        Crossing cB = byName(crossings, "B");

        // Each crossing must be at its centre.
        assertTrue(distanceFromCrossingToTriggerMeters(cA, a) <= CLOSEST_TOL_M,
                "A crossing must be at A's centre");
        assertTrue(distanceFromCrossingToTriggerMeters(cB, b) <= CLOSEST_TOL_M,
                "B crossing must be at B's centre");

        // Reported time A→B must be the centre-to-centre transit (300 s),
        // NOT edge-to-edge (260 s) that the old detector would emit.
        double timeAtoBsec =
                (cB.gpsTrackDataPoint.getPointTimestamp().getTime()
                 - cA.gpsTrackDataPoint.getPointTimestamp().getTime()) / 1000.0;

        double expectedCenterToCenterSec = 300.0 / SPEED_MPS; // = 300 s
        assertEquals(expectedCenterToCenterSec, timeAtoBsec, 2.0,
                "A→B time must match centre-to-centre transit. Old edge-based detector would " +
                "report ~" + (expectedCenterToCenterSec - 2 * radius / SPEED_MPS) + " s instead.");

        // And the delta distance (as carried on the crossing points) must match too.
        double distAtoBm = cB.gpsTrackDataPoint.getDistanceInMeterSinceStart()
                           - cA.gpsTrackDataPoint.getDistanceInMeterSinceStart();
        assertEquals(300.0, distAtoBm, 2.0,
                "Distance A→B must be centre-to-centre (~300 m)");
    }

    /**
     * A tangent segment that dips into the zone and exits within the same
     * segment (both endpoints outside) must emit exactly one crossing at the
     * closest-approach point — not skip the zone, and not emit two crossings.
     */
    @Test
    @DisplayName("Tangent pass through a zone emits one crossing at the closest approach")
    void tangentPassEmitsSingleCrossingAtClosestApproach() {
        // Coarse sampling: straight east, samples at x=0, 200, 400. Trigger at (200, 8) r=20.
        // The middle sample sits 8 m from the centre (inside), so this is actually
        // an enter/exit across multiple segments — test a truly tangent case:
        // samples at x=0 and x=400 only; both outside; the line grazes (200, 8).
        double[][] pathMeters = new double[][]{
                {0, 0}, {400, 0}
        };
        TriggerPoint a = trigger("A", 200.0, 8.0);
        double radius = 20.0;

        List<Crossing> crossings = runSingleTrigger(pathMeters, a, radius);

        assertEquals(1, crossings.size(),
                "A tangent pass (both endpoints outside, segment dips in) must still emit exactly one crossing");
        double distM = distanceFromCrossingToTriggerMeters(crossings.get(0), a);
        // Closest approach is 8 m from the centre (the perpendicular foot).
        assertTrue(distM <= 8.0 + CLOSEST_TOL_M,
                "Tangent crossing must be at the perpendicular foot (~8 m from centre), was " + distM + " m");
    }

    /**
     * Dense sampling inside a zone must still produce only one crossing per
     * visit, and that crossing must be the sample closest to the centre.
     */
    @Test
    @DisplayName("Dense samples inside a zone produce one crossing, at the closest sample to the centre")
    void denseSamplingInsideZoneProducesSingleClosestCrossing() {
        // Straight eastbound: many samples between x=80 and x=120 (zone radius 20 around x=100).
        double[][] pathMeters = new double[][]{
                {0, 0}, {80, 0}, {85, 0}, {90, 0}, {95, 0},
                {100, 0}, {105, 0}, {110, 0}, {115, 0}, {120, 0},
                {200, 0}
        };
        TriggerPoint a = trigger("A", 100.0, 0.0);
        double radius = 20.0;

        List<Crossing> crossings = runSingleTrigger(pathMeters, a, radius);

        assertEquals(1, crossings.size(), "Dense sampling inside the zone must still produce exactly one crossing");
        double distM = distanceFromCrossingToTriggerMeters(crossings.get(0), a);
        assertTrue(distM <= CLOSEST_TOL_M,
                "With a sample exactly at the centre, the crossing must be at the centre (was " + distM + " m away)");
    }

    /**
     * If the track <b>starts</b> already inside the zone, the detector must
     * still emit a crossing at the closest-to-centre point of the visit.
     */
    @Test
    @DisplayName("Track starting inside a zone still emits a closest-to-centre crossing")
    void trackStartingInsideZoneEmitsClosestCrossing() {
        // First sample is at x=95 (inside radius 20 around x=100), then sample at centre, then exit.
        double[][] pathMeters = new double[][]{
                {95, 0}, {100, 0}, {110, 0}, {150, 0}
        };
        TriggerPoint a = trigger("A", 100.0, 0.0);
        double radius = 20.0;

        List<Crossing> crossings = runSingleTrigger(pathMeters, a, radius);

        assertEquals(1, crossings.size(),
                "A visit that the track starts inside must still produce exactly one crossing");
        double distM = distanceFromCrossingToTriggerMeters(crossings.get(0), a);
        assertTrue(distM <= CLOSEST_TOL_M,
                "Crossing must be at the closest-to-centre sample (the centre), was " + distM + " m");
    }

    /**
     * If the track <b>ends</b> inside the zone without leaving, the detector
     * must still flush a crossing at the closest-to-centre point of the visit.
     */
    @Test
    @DisplayName("Track ending inside a zone still emits a closest-to-centre crossing")
    void trackEndingInsideZoneEmitsClosestCrossing() {
        // Enter, reach centre, stop inside. No OUTSIDE transition at the end.
        double[][] pathMeters = new double[][]{
                {0, 0}, {80, 0}, {90, 0}, {100, 0}, {105, 0}
        };
        TriggerPoint a = trigger("A", 100.0, 0.0);
        double radius = 20.0;

        List<Crossing> crossings = runSingleTrigger(pathMeters, a, radius);

        assertEquals(1, crossings.size(),
                "A visit that the track ends inside must still produce exactly one crossing (flushed at end)");
        double distM = distanceFromCrossingToTriggerMeters(crossings.get(0), a);
        assertTrue(distM <= CLOSEST_TOL_M,
                "Flushed crossing must be at the closest-to-centre sample, was " + distM + " m");
    }

    /**
     * Two separate passes through the same trigger (out-and-back) must yield
     * two distinct crossings, each at the respective closest-to-centre point.
     */
    @Test
    @DisplayName("Two visits of the same trigger produce two crossings, each at the centre")
    void twoVisitsOfSameTriggerProduceTwoCenteredCrossings() {
        // Eastbound through A, leave far east, return westbound through A again.
        double[][] pathMeters = new double[][]{
                {0, 0}, {100, 0}, {200, 0},   // pass 1: west -> through centre -> east
                {200, 50},                     // detour north so we clearly leave the zone
                {200, 0}, {100, 0}, {0, 0}     // pass 2: east -> through centre -> west
        };
        TriggerPoint a = trigger("A", 100.0, 0.0);
        double radius = 20.0;

        List<Crossing> crossings = runSingleTrigger(pathMeters, a, radius);

        assertEquals(2, crossings.size(), "Two separate visits must produce two crossings");
        for (Crossing c : crossings) {
            double distM = distanceFromCrossingToTriggerMeters(c, a);
            assertTrue(distM <= CLOSEST_TOL_M,
                    "Each visit's crossing must be at the centre, was " + distM + " m");
        }
    }

    @Test
    @DisplayName("Deltas are recomputed after sorting crossings from the same GPS segment")
    void deltasAreChronologicalWhenTriggerOrderDiffersFromTravelOrder() {
        double[][] pathMeters = new double[][]{
                {0, 0}, {400, 0}
        };
        TriggerPoint a = trigger("A", 300.0, 0.0);
        TriggerPoint b = trigger("B", 100.0, 0.0);
        double radius = 20.0;

        List<Crossing> crossings = runRequest(pathMeters, List.of(a, b), radius);

        assertEquals(2, crossings.size());
        assertEquals("B", crossings.get(0).triggerPoint.name,
                "Crossings must be sorted by interpolated timestamp, not trigger-list order");
        assertEquals("A", crossings.get(1).triggerPoint.name);
        assertEquals(0.0, crossings.get(0).timeInSecSinceLastTriggerPoint, 0.01);
        assertEquals(200.0, crossings.get(1).timeInSecSinceLastTriggerPoint, 2.0,
                "A delta must be measured from chronological predecessor B");
        assertEquals(200.0, crossings.get(1).distanceInMeterSinceLastTriggerPoint, 2.0);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private List<Crossing> runSingleTrigger(double[][] pathMeters, TriggerPoint a, double radius) {
        return runRequest(pathMeters, List.of(a), radius);
    }

    private List<Crossing> runTwoTriggers(double[][] pathMeters, TriggerPoint a, TriggerPoint b, double radius) {
        return runRequest(pathMeters, List.of(a, b), radius);
    }

    private List<Crossing> runRequest(double[][] pathMeters, List<TriggerPoint> triggers, double radius) {
        List<GpsTrackDataPoint> points = buildTrack(pathMeters);

        GpsTrack track = new GpsTrack();
        track.setId(TRACK_ID);
        track.setTrackName("test-track");

        for (TriggerPoint tp : triggers) {
            when(gpsTrackRepository.getTracksWithinDistanceToPoint(
                    eq(tp.coordinate.x), eq(tp.coordinate.y), eq(radius), any()))
                    .thenReturn(List.of(TRACK_ID));
        }
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track));
        when(gpsTrackVariantSelector.forMetricsId(TRACK_ID)).thenReturn(TRACK_DATA_ID);
        when(gpsTrackDataPointRepository.findAllByGpsTrackDataIdOrderByPointIndexAsc(TRACK_DATA_ID))
                .thenReturn(points);

        CrossingPointsRequest request = new CrossingPointsRequest();
        request.setTriggerPoints(triggers);
        request.setRadius(radius);

        CrossingPointsResponse response = service.getTrackTimeBetweenPoints(request, null);
        assertNotNull(response, "Response must not be null");
        assertNotNull(response.getCrossings(), "Crossings map must not be null");

        CrossingsPerTrack perTrack = response.getCrossings().get(TRACK_ID);
        assertNotNull(perTrack, "Track must be present in the response");
        List<Crossing> crossings = perTrack.getCrossings();
        assertNotNull(crossings, "Crossings list must not be null");
        return crossings;
    }

    private List<GpsTrackDataPoint> buildTrack(double[][] pathMeters) {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        double cumulative = 0.0;
        for (int i = 0; i < pathMeters.length; i++) {
            GpsTrackDataPoint p = new GpsTrackDataPoint();
            p.setPointIndex(i);

            double lon = pathMeters[i][0] / METERS_PER_DEG_LON;
            double lat = pathMeters[i][1] / METERS_PER_DEG_LAT;
            Point jts = factory.createPoint(new Coordinate(lon, lat, 0.0));
            p.setPointLongLat(jts);

            if (i > 0) {
                double dx = pathMeters[i][0] - pathMeters[i - 1][0];
                double dy = pathMeters[i][1] - pathMeters[i - 1][1];
                cumulative += Math.sqrt(dx * dx + dy * dy);
            }
            p.setDistanceInMeterSinceStart(cumulative);
            // time at 1 m/s => timestamp offset in ms = cumulative * 1000 / SPEED_MPS
            long offsetMs = (long) (cumulative * 1000.0 / SPEED_MPS);
            p.setPointTimestamp(new Date(START_TIME + offsetMs));
            points.add(p);
        }
        return points;
    }

    private TriggerPoint trigger(String name, double xMeters, double yMeters) {
        TriggerPoint tp = new TriggerPoint();
        tp.name = name;
        tp.coordinate = new Coordinate(
                xMeters / METERS_PER_DEG_LON,
                yMeters / METERS_PER_DEG_LAT,
                0.0
        );
        return tp;
    }

    private static Crossing byName(List<Crossing> crossings, String name) {
        return crossings.stream()
                .filter(c -> name.equals(c.triggerPoint.name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing crossing for trigger " + name));
    }

    private static double distanceFromCrossingToTriggerMeters(Crossing c, TriggerPoint tp) {
        return GPXReader.getDistanceBetweenTwoWGS84(
                tp.coordinate,
                c.gpsTrackDataPoint.getPointLongLat().getCoordinate()
        );
    }
}
