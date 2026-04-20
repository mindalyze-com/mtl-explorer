package com.x8ing.mtl.server.mtlserver.logic.crossing;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
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
 * Tests for per-segment "stop" detection annotated onto each {@link Crossing}
 * by {@link TrackTimeBetweenTwoPoints}. A stop is a contiguous run of samples
 * where speed stays below 0.5 km/h for at least 30 s.
 */
class TrackTimeBetweenTwoPointsStopDetectionTest {

    @Mock
    private GpsTrackRepository gpsTrackRepository;
    @Mock
    private GpsTrackVariantSelector gpsTrackVariantSelector;
    @Mock
    private GpsTrackDataPointRepository gpsTrackDataPointRepository;

    private TrackTimeBetweenTwoPoints service;

    private final GeometryFactory factory = new GeometryFactory();

    private static final double METERS_PER_DEG_LON = 111320.0;
    private static final double METERS_PER_DEG_LAT = 110540.0;
    private static final Long TRACK_ID = 1L;
    private static final Long TRACK_DATA_ID = 10L;
    private static final long START_TIME = 1_700_000_000_000L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TrackTimeBetweenTwoPoints(
                gpsTrackRepository, gpsTrackDataPointRepository, gpsTrackVariantSelector);
    }

    @Test
    @DisplayName("First crossing has no segment notes (no preceding segment)")
    void firstCrossingHasNoSegmentNotes() {
        // A straight eastbound track, moving steadily through A then B.
        List<GpsTrackDataPoint> points = track(
                sample(0, 0, 0),
                sample(100, 100, 0),
                sample(200, 200, 0),
                sample(300, 300, 0),
                sample(400, 400, 0),
                sample(500, 500, 0),
                sample(600, 600, 0)
        );
        List<Crossing> crossings = run(points,
                trigger("A", 100, 0),
                trigger("B", 500, 0),
                20.0);

        assertEquals(2, crossings.size());
        assertNull(crossings.get(0).segmentNotesSinceLastTriggerPoint,
                "First crossing must not carry segment notes");
        assertNotNull(crossings.get(1).segmentNotesSinceLastTriggerPoint);
    }

    @Test
    @DisplayName("Steady movement A→B produces zero stops")
    void steadyMovementHasNoStops() {
        List<GpsTrackDataPoint> points = track(
                sample(0, 0, 0),
                sample(100, 100, 0),
                sample(200, 200, 0),
                sample(300, 300, 0),
                sample(400, 400, 0),
                sample(500, 500, 0),
                sample(600, 600, 0)
        );
        List<Crossing> crossings = run(points,
                trigger("A", 100, 0),
                trigger("B", 500, 0),
                20.0);

        SegmentNotes notes = crossings.get(1).segmentNotesSinceLastTriggerPoint;
        assertNotNull(notes);
        assertEquals(0, notes.stopCount, "No slow samples → no stops");
        assertEquals(0.0, notes.totalStoppedSec);
        assertEquals(0.0, notes.longestStopSec);
    }

    @Test
    @DisplayName("A 2-minute stop inside the segment is detected")
    void twoMinuteStopIsDetected() {
        // Move to 200 m, sit there for 120 s (stationary samples at same position,
        // 10 s apart), then continue moving. Stop clearly exceeds 30 s threshold.
        List<GpsTrackDataPoint> points = new ArrayList<>();
        // Moving from A at x=100 m to x=200 m at 1 m/s.
        for (int t = 0; t <= 200; t += 20) {
            points.add(sample(t, t, 0));
        }
        // Stop: 120 s at x=200 m, samples every 10 s.
        for (int t = 210; t <= 320; t += 10) {
            points.add(sample(t, 200, 0));
        }
        // Resume to B at x=500 m.
        for (int t = 330; t <= 620; t += 20) {
            double x = 200 + (t - 320);
            points.add(sample(t, x, 0));
        }

        List<Crossing> crossings = run(points,
                trigger("A", 100, 0),
                trigger("B", 500, 0),
                20.0);

        SegmentNotes notes = crossings.get(1).segmentNotesSinceLastTriggerPoint;
        assertNotNull(notes);
        assertEquals(1, notes.stopCount, "One 2-minute pause must be counted as one stop");
        assertTrue(notes.totalStoppedSec >= 100.0 && notes.totalStoppedSec <= 130.0,
                "Stopped duration ~120 s, got " + notes.totalStoppedSec);
        assertEquals(notes.totalStoppedSec, notes.longestStopSec, 0.01,
                "With a single stop, longest == total");
    }

    @Test
    @DisplayName("Pause shorter than threshold (10 s) is ignored — GPS jitter noise")
    void shortPauseBelowThresholdIsIgnored() {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int t = 0; t <= 200; t += 20) {
            points.add(sample(t, t, 0));
        }
        // 10-second "stop" — under MIN_STOP_SEC=30.
        points.add(sample(210, 200, 0));
        points.add(sample(220, 200, 0));
        for (int t = 230; t <= 520; t += 20) {
            double x = 200 + (t - 220);
            points.add(sample(t, x, 0));
        }

        List<Crossing> crossings = run(points,
                trigger("A", 100, 0),
                trigger("B", 500, 0),
                20.0);

        SegmentNotes notes = crossings.get(1).segmentNotesSinceLastTriggerPoint;
        assertNotNull(notes);
        assertEquals(0, notes.stopCount, "Short pauses must not count as a stop (GPS jitter/red light)");
    }

    @Test
    @DisplayName("Two separate stops inside the same segment are each counted")
    void twoSeparateStopsAreEachCounted() {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        // Move A(100) → x=200.
        for (int t = 0; t <= 200; t += 20) {
            points.add(sample(t, t, 0));
        }
        // Stop 1: 60 s.
        for (int t = 210; t <= 260; t += 10) {
            points.add(sample(t, 200, 0));
        }
        // Move to x=350.
        for (int t = 270; t <= 420; t += 20) {
            double x = 200 + (t - 260);
            points.add(sample(t, x, 0));
        }
        // Stop 2: 45 s.
        for (int t = 430; t <= 475; t += 10) {
            points.add(sample(t, 360, 0));
        }
        // Move to B.
        for (int t = 485; t <= 630; t += 20) {
            double x = 360 + (t - 475);
            points.add(sample(t, x, 0));
        }

        List<Crossing> crossings = run(points,
                trigger("A", 100, 0),
                trigger("B", 500, 0),
                20.0);

        SegmentNotes notes = crossings.get(1).segmentNotesSinceLastTriggerPoint;
        assertNotNull(notes);
        assertEquals(2, notes.stopCount, "Two separate pauses must each count as a stop");
        assertTrue(notes.longestStopSec >= 50.0 && notes.longestStopSec <= 70.0,
                "Longest stop ~60 s, got " + notes.longestStopSec);
        assertTrue(notes.totalStoppedSec >= 90.0 && notes.totalStoppedSec <= 120.0,
                "Total stopped ~105 s (60 + 45), got " + notes.totalStoppedSec);
    }

    @Test
    @DisplayName("Samples outside the segment boundary do not contribute to stops")
    void stopOutsideSegmentIsIgnored() {
        // Racer sits for 2 minutes BEFORE reaching A, then moves cleanly to B.
        List<GpsTrackDataPoint> points = new ArrayList<>();
        // Stationary at origin for 120 s (pre-A).
        for (int t = 0; t <= 120; t += 10) {
            points.add(sample(t, 0, 0));
        }
        // Move A(100) → B(500) cleanly.
        for (int t = 130; t <= 630; t += 20) {
            double x = 0 + (t - 120);
            points.add(sample(t, x, 0));
        }

        List<Crossing> crossings = run(points,
                trigger("A", 100, 0),
                trigger("B", 500, 0),
                20.0);

        SegmentNotes notes = crossings.get(1).segmentNotesSinceLastTriggerPoint;
        assertNotNull(notes);
        assertEquals(0, notes.stopCount,
                "A stop that happens BEFORE reaching A must not be counted in the A→B segment");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<Crossing> run(List<GpsTrackDataPoint> points, TriggerPoint a, TriggerPoint b, double radius) {
        GpsTrack track = new GpsTrack();
        track.setId(TRACK_ID);
        track.setTrackName("test");

        for (TriggerPoint tp : List.of(a, b)) {
            when(gpsTrackRepository.getTracksWithinDistanceToPoint(
                    eq(tp.coordinate.x), eq(tp.coordinate.y), eq(radius), any()))
                    .thenReturn(List.of(TRACK_ID));
        }
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track));
        when(gpsTrackVariantSelector.forMetricsId(TRACK_ID)).thenReturn(TRACK_DATA_ID);
        when(gpsTrackDataPointRepository.findAllByGpsTrackDataIdOrderByPointIndexAsc(TRACK_DATA_ID))
                .thenReturn(points);

        CrossingPointsRequest request = new CrossingPointsRequest();
        request.setTriggerPoints(List.of(a, b));
        request.setRadius(radius);

        CrossingPointsResponse response = service.getTrackTimeBetweenPoints(request, null);
        assertNotNull(response);
        CrossingsPerTrack perTrack = response.getCrossings().get(TRACK_ID);
        assertNotNull(perTrack);
        return perTrack.getCrossings();
    }

    private List<GpsTrackDataPoint> track(GpsTrackDataPoint... arr) {
        List<GpsTrackDataPoint> l = new ArrayList<>(List.of(arr));
        // compute cumulative distance in meters since start (straight-line).
        double cumulative = 0.0;
        for (int i = 0; i < l.size(); i++) {
            GpsTrackDataPoint p = l.get(i);
            if (i > 0) {
                GpsTrackDataPoint q = l.get(i - 1);
                double dx = (p.getPointLongLat().getX() - q.getPointLongLat().getX()) * METERS_PER_DEG_LON;
                double dy = (p.getPointLongLat().getY() - q.getPointLongLat().getY()) * METERS_PER_DEG_LAT;
                cumulative += Math.sqrt(dx * dx + dy * dy);
            }
            p.setDistanceInMeterSinceStart(cumulative);
            p.setPointIndex(i);
        }
        return l;
    }

    /**
     * Build a raw point located at (xMeters, yMeters) with a timestamp at startTime+tSec.
     */
    private GpsTrackDataPoint sample(int tSec, double xMeters, double yMeters) {
        GpsTrackDataPoint p = new GpsTrackDataPoint();
        double lon = xMeters / METERS_PER_DEG_LON;
        double lat = yMeters / METERS_PER_DEG_LAT;
        Point jts = factory.createPoint(new Coordinate(lon, lat, 0.0));
        p.setPointLongLat(jts);
        p.setPointTimestamp(new Date(START_TIME + tSec * 1000L));
        return p;
    }

    private TriggerPoint trigger(String name, double xMeters, double yMeters) {
        TriggerPoint tp = new TriggerPoint();
        tp.name = name;
        tp.coordinate = new Coordinate(
                xMeters / METERS_PER_DEG_LON,
                yMeters / METERS_PER_DEG_LAT,
                0.0);
        return tp;
    }
}
