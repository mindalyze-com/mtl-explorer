package com.x8ing.mtl.server.mtlserver.gpx;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Compatibility contract captured by executing these synthetic inputs through
 * the original GeoTools 35.1 implementation before removing GeoTools.
 */
class GPXReaderGeoToolsCompatibilityTest {

    private static final String ORACLE_RESOURCE = "/gpx/geotools-35.1-oracle.psv";
    private static final int EXPECTED_DISTANCE_CASES = 336;
    private static final int EXPECTED_PROJECTION_CASES = 200;
    private static final double MINIMUM_DISTANCE_TOLERANCE_M = 0.001;
    private static final double RELATIVE_DISTANCE_TOLERANCE = 1e-10;
    private static final double MINIMUM_PROJECTION_TOLERANCE_M = 0.000_001;
    private static final double RELATIVE_PROJECTION_TOLERANCE = 1e-13;

    @TestFactory
    Stream<DynamicTest> distancesMatchGeoToolsOracleInBothDirections() {
        List<OracleRow> rows = oracleRows("distance");
        assertEquals(EXPECTED_DISTANCE_CASES, rows.size(), "distance oracle case count");

        return rows.stream().map(row -> DynamicTest.dynamicTest(row.name(), () -> {
            Coordinate start = new Coordinate(row.input1(), row.input2());
            Coordinate end = new Coordinate(row.input3(), row.input4());
            double tolerance = Math.max(
                    MINIMUM_DISTANCE_TOLERANCE_M,
                    Math.abs(row.expected1()) * RELATIVE_DISTANCE_TOLERANCE);

            assertEquals(
                    row.expected1(),
                    GPXReader.getDistanceBetweenTwoWGS84(start, end),
                    tolerance,
                    "forward distance in meters");
            assertEquals(
                    row.expected1(),
                    GPXReader.getDistanceBetweenTwoWGS84(end, start),
                    tolerance,
                    "reverse distance in meters");
        }));
    }

    @TestFactory
    Stream<DynamicTest> webMercatorMatchesGeoToolsOracleAcrossSupportedArea() {
        List<OracleRow> rows = oracleRows("projection");
        assertEquals(EXPECTED_PROJECTION_CASES, rows.size(), "projection oracle case count");
        GPXReader reader = new GPXReader();

        return rows.stream().map(row -> DynamicTest.dynamicTest(row.name(), () -> {
            Point actual = reader.convertLongLatWgs84ToPlanarWebMercator(
                    row.input1(), row.input2());
            double xTolerance = Math.max(
                    MINIMUM_PROJECTION_TOLERANCE_M,
                    Math.abs(row.expected1()) * RELATIVE_PROJECTION_TOLERANCE);
            double yTolerance = Math.max(
                    MINIMUM_PROJECTION_TOLERANCE_M,
                    Math.abs(row.expected2()) * RELATIVE_PROJECTION_TOLERANCE);

            assertEquals(row.expected1(), actual.getX(), xTolerance, "EPSG:3857 x");
            assertEquals(row.expected2(), actual.getY(), yTolerance, "EPSG:3857 y");
            assertEquals(3857, actual.getSRID());
        }));
    }

    @Test
    void distanceRejectsNonFiniteLongitudeLikeGeoTools() {
        Coordinate valid = new Coordinate(0.0, 0.0);

        assertThrows(IllegalArgumentException.class,
                () -> GPXReader.getDistanceBetweenTwoWGS84(new Coordinate(Double.NaN, 0.0), valid));
        assertThrows(IllegalArgumentException.class,
                () -> GPXReader.getDistanceBetweenTwoWGS84(new Coordinate(Double.POSITIVE_INFINITY, 0.0), valid));
    }

    @Test
    void distanceRejectsLatitudeOutsideWgs84RangeLikeGeoTools() {
        Coordinate valid = new Coordinate(0.0, 0.0);

        assertThrows(IllegalArgumentException.class,
                () -> GPXReader.getDistanceBetweenTwoWGS84(new Coordinate(0.0, 90.000_001), valid));
        assertThrows(IllegalArgumentException.class,
                () -> GPXReader.getDistanceBetweenTwoWGS84(new Coordinate(0.0, -90.000_001), valid));
    }

    private static List<OracleRow> oracleRows(String kind) {
        try (InputStream input = Objects.requireNonNull(
                GPXReaderGeoToolsCompatibilityTest.class.getResourceAsStream(ORACLE_RESOURCE),
                "Missing " + ORACLE_RESOURCE);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .skip(1)
                    .map(OracleRow::parse)
                    .filter(row -> row.kind().equals(kind))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + ORACLE_RESOURCE, exception);
        }
    }

    private record OracleRow(
            String kind,
            String name,
            double input1,
            double input2,
            double input3,
            double input4,
            double expected1,
            double expected2) {

        private static OracleRow parse(String line) {
            String[] fields = line.split("\\|", -1);
            if (fields.length != 8) {
                throw new IllegalArgumentException("Malformed oracle row: " + line);
            }
            return new OracleRow(
                    fields[0],
                    fields[1],
                    parseDouble(fields[2]),
                    parseDouble(fields[3]),
                    parseDouble(fields[4]),
                    parseDouble(fields[5]),
                    parseDouble(fields[6]),
                    parseDouble(fields[7]));
        }

        private static double parseDouble(String value) {
            return value.isEmpty() ? Double.NaN : Double.parseDouble(value);
        }
    }
}
