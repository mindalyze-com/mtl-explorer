package com.x8ing.mtl.server.mtlserver.gpx;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordedPathGeometryTest {
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void isolatedObservationsArePreservedWithoutConnectingRecordingSections() {
        var line = factory.createLineString(new Coordinate[]{new Coordinate(8, 47), new Coordinate(9, 47), new Coordinate(9.001, 47)});
        var result = RecordedPathGeometry.candidates(line, List.of(1));
        assertEquals(2, result.getNumGeometries());
        assertEquals("Point", result.getGeometryN(0).getGeometryType());
        assertEquals(new Coordinate(8, 47), result.getGeometryN(0).getCoordinate());
        assertEquals(4326, result.getSRID());
    }

    @Test
    void longitudeInterpolationFollowsTheShortWrappedArc() {
        var a = new Coordinate(179.999, 47);
        var b = new Coordinate(-179.999, 47);
        assertEquals(180d, RecordedPathGeometry.interpolate(a, b, .5).x, 1e-9);
        var result = RecordedPathGeometry.candidates(factory.createLineString(new Coordinate[]{a, b}), List.of());
        assertEquals(2, result.getNumPoints());
    }

    @Test
    void longPolarEdgesAreDensifiedAlongTheRecordedCurve() {
        var result = RecordedPathGeometry.candidates(factory.createLineString(new Coordinate[]{
                new Coordinate(0, 89.999), new Coordinate(179, 89.999)}), List.of());
        assertTrue(result.getNumPoints() > 10_000);
        for (var coordinate : result.getCoordinates()) assertEquals(89.999, coordinate.y);
    }
}
