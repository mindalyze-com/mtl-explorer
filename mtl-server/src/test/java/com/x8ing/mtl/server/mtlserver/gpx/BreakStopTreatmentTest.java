package com.x8ing.mtl.server.mtlserver.gpx;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BreakStopTreatment}. All scenarios use synthetic
 * coordinate sequences in WGS-84 with epoch-second timestamps in the M
 * ordinate.
 */
class BreakStopTreatmentTest {

    /**
     * About 111.32 m per 0.001 degree latitude near the equator.
     */
    private static final double M_PER_DEG_LAT = 111_320.0;

    private static Coordinate at(double lng, double lat, double t) {
        return new CoordinateXYZM(lng, lat, 100.0, t);
    }

    /**
     * Convert a north offset in meters to a latitude delta near (lat=0).
     */
    private static double latOffset(double meters) {
        return meters / M_PER_DEG_LAT;
    }

    // spike removal

    @Test
    void removesClassicAToBToCSpike() {
        // A at origin, B 110 m north (jump), C back at origin (return).
        List<Coordinate> in = List.of(
                at(0.0, 0.0, 0),
                at(0.0, latOffset(110), 5),
                at(0.0, 0.0, 10),
                at(0.0, latOffset(2), 15)
        );
        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);
        assertEquals(1, r.spikesRemoved());
        // 4 input - 1 spike, no stop collapse expected (only 15 s of data)
        assertEquals(3, r.cleanedCoordinates().size());
    }

    @Test
    void removesSupportedSpikeReturningNearDriftCloud() {
        // A and C are both plausible points in a loose indoor drift cloud. The
        // fourth point supports that B was an isolated jump, not real movement.
        List<Coordinate> in = List.of(
                at(0.0, 0.0, 0),
                at(0.0, latOffset(140), 5),
                at(latOffset(45), 0.0, 10),
                at(latOffset(47), 0.0, 15)
        );

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);

        assertEquals(1, r.spikesRemoved());
        assertEquals(3, r.cleanedCoordinates().size());
    }

    @Test
    void keepsRealMovementThatHappensToLookLikeAJump() {
        // A-B 80 m, B-C another 80 m further north. No return: not a spike.
        List<Coordinate> in = List.of(
                at(0.0, 0.0, 0),
                at(0.0, latOffset(80), 5),
                at(0.0, latOffset(160), 10)
        );
        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);
        assertEquals(0, r.spikesRemoved());
        assertEquals(3, r.cleanedCoordinates().size());
    }

    @Test
    void retainsStationaryObservationsForLaterMotionAnalysis() {
        List<Coordinate> input = new ArrayList<>();
        for (int t = 0; t <= 300; t += 5) input.add(at(0, 0, t));
        var result = BreakStopTreatment.apply(input);
        assertEquals(input, result.cleanedCoordinates());
        assertEquals(0, result.collapsedPoints());
        assertTrue(result.stops().isEmpty());
        for (int i = 0; i < input.size(); i++) assertSame(input.get(i), result.cleanedCoordinates().get(i));
    }

    @Test
    void doesNotTreatSparseRepeatedFixesAsStopAnchors() {
        List<Coordinate> input = List.of(at(0, 0, 0), at(0, 0, 600), at(0, 0, 1200));
        var result = BreakStopTreatment.apply(input);
        assertEquals(input, result.cleanedCoordinates());
        assertTrue(result.stops().isEmpty());
    }

    @Test
    void emptyAndTinyInputIsPassThrough() {
        assertTrue(BreakStopTreatment.apply(null).cleanedCoordinates().isEmpty());
        for (int n = 0; n < 3; n++) {
            List<Coordinate> input = new ArrayList<>();
            for (int i = 0; i < n; i++) input.add(at(0, 0, i));
            assertEquals(input, BreakStopTreatment.apply(input).cleanedCoordinates());
        }
    }
}
