package com.x8ing.mtl.server.mtlserver.gpx;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for GPXReader distance utility.
 * No Spring context required.
 */
class GPXReaderDistanceTest {

    @Test
    void identicalCoordinates_returnsZero() {
        Coordinate c = new Coordinate(8.50784, 47.55841);
        assertEquals(0.0, GPXReader.getDistanceBetweenTwoWGS84(c, c));
    }

    @Test
    void knownDistance_reasonable() {
        // Zurich HB to Bern HB is roughly 95-100 km
        Coordinate zurich = new Coordinate(8.5403, 47.3782);
        Coordinate bern = new Coordinate(7.4393, 46.9481);
        double distance = GPXReader.getDistanceBetweenTwoWGS84(zurich, bern);
        assertTrue(distance > 90_000 && distance < 110_000,
                "Expected ~100 km, got " + Math.round(distance) + " m");
    }

    @Test
    void veryClosePoints_noException() {
        // Two points ~1m apart
        Coordinate c1 = new Coordinate(8.50784, 47.55841);
        Coordinate c2 = new Coordinate(8.50785, 47.55841);
        assertDoesNotThrow(() -> GPXReader.getDistanceBetweenTwoWGS84(c1, c2));
        assertTrue(GPXReader.getDistanceBetweenTwoWGS84(c1, c2) > 0);
    }
}
