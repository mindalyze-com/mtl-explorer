package com.x8ing.mtl.server.mtlserver.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoCoordinateUtilsTest {

    @Test
    void acceptsWgs84BoundaryCoordinates() {
        assertTrue(GeoCoordinateUtils.isValidLatitude(-90.0));
        assertTrue(GeoCoordinateUtils.isValidLatitude(90.0));
        assertTrue(GeoCoordinateUtils.isValidLongitude(-180.0));
        assertTrue(GeoCoordinateUtils.isValidLongitude(180.0));
    }

    @Test
    void rejectsNonFiniteAndOutOfRangeCoordinates() {
        assertFalse(GeoCoordinateUtils.isValidLatitude(Double.NaN));
        assertFalse(GeoCoordinateUtils.isValidLatitude(90.1));
        assertFalse(GeoCoordinateUtils.isValidLongitude(Double.POSITIVE_INFINITY));
        assertFalse(GeoCoordinateUtils.isValidLongitude(-180.1));
    }

    @Test
    void requiredCoordinateChecksUseTheSharedBounds() {
        assertDoesNotThrow(() -> GeoCoordinateUtils.requireValidLatitude(47.0));
        assertDoesNotThrow(() -> GeoCoordinateUtils.requireValidLongitude(8.0));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinateUtils.requireValidLatitude(91.0));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinateUtils.requireValidLongitude(181.0));
    }
}
