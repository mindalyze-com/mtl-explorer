package com.x8ing.mtl.server.mtlserver.gpx;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class StartupElevationStabilizerTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void garminStartupSequence_correctsLeadingElevations() {
        LineString input = garminStartupLineString();

        StartupElevationStabilizer.Result result = StartupElevationStabilizer.stabilize(input);

        assertTrue(result.corrected());
        assertEquals(3, result.correctedPointCount());
        assertEquals(474.0, result.baselineElevation(), 0.5);
        assertEquals(input.getNumPoints(), result.lineString().getNumPoints());

        for (int i = 0; i < result.correctedPointCount(); i++) {
            Coordinate original = input.getCoordinateN(i);
            Coordinate corrected = result.lineString().getCoordinateN(i);
            assertEquals(original.getX(), corrected.getX(), 1e-12);
            assertEquals(original.getY(), corrected.getY(), 1e-12);
            assertEquals(original.getM(), corrected.getM(), 1e-12);
            assertEquals(result.baselineElevation(), corrected.getZ(), 1e-12);
        }
        assertEquals(471.8, result.lineString().getCoordinateN(3).getZ(), 1e-12);
    }

    @Test
    void track100210ExactRawStartup_correctsGarminElevationJump() {
        LineString input = track100210RawStartupLineString();

        assertEquals(108.4, input.getCoordinateN(1).getZ() - input.getCoordinateN(0).getZ(), 1e-12);
        assertEquals(1.0, input.getCoordinateN(1).getM() - input.getCoordinateN(0).getM(), 1e-12);

        StartupElevationStabilizer.Result result = StartupElevationStabilizer.stabilize(input);

        assertTrue(result.corrected());
        assertEquals(3, result.correctedPointCount());
        assertEquals(474.5, result.baselineElevation(), 0.2);

        LineString stabilized = result.lineString();
        assertEquals(input.getNumPoints(), stabilized.getNumPoints());
        assertEquals(input.getCoordinateN(0).getX(), stabilized.getCoordinateN(0).getX(), 1e-12);
        assertEquals(input.getCoordinateN(0).getY(), stabilized.getCoordinateN(0).getY(), 1e-12);
        assertEquals(input.getCoordinateN(0).getM(), stabilized.getCoordinateN(0).getM(), 1e-12);
        assertEquals(result.baselineElevation(), stabilized.getCoordinateN(0).getZ(), 1e-12);
        assertEquals(result.baselineElevation(), stabilized.getCoordinateN(1).getZ(), 1e-12);
        assertEquals(result.baselineElevation(), stabilized.getCoordinateN(2).getZ(), 1e-12);
        assertEquals(471.8, stabilized.getCoordinateN(3).getZ(), 1e-12);
        assertTrue(Math.abs(stabilized.getCoordinateN(1).getZ() - stabilized.getCoordinateN(0).getZ()) < 0.1);
    }

    @Test
    void stableStartup_isUnchanged() {
        LineString input = steadyLineString(20, 500.0, 0.0);

        StartupElevationStabilizer.Result result = StartupElevationStabilizer.stabilize(input);

        assertFalse(result.corrected());
        assertSame(input, result.lineString());
    }

    @Test
    void sparseStartup_isUnchanged() {
        LineString input = garminLikeLineString(11);

        StartupElevationStabilizer.Result result = StartupElevationStabilizer.stabilize(input);

        assertFalse(result.corrected());
        assertSame(input, result.lineString());
    }

    @Test
    void realSteadyClimb_isUnchanged() {
        LineString input = steadyLineString(25, 1000.0, 3.0);

        StartupElevationStabilizer.Result result = StartupElevationStabilizer.stabilize(input);

        assertFalse(result.corrected());
        assertSame(input, result.lineString());
    }

    @Test
    void midTrackElevationSpike_isUnchanged() {
        LineString input = steadyLineString(30, 500.0, 0.0);
        Coordinate[] coords = copyCoordinates(input);
        coords[20] = new CoordinateXYZM(coords[20].getX(), coords[20].getY(), 600.0, coords[20].getM());
        LineString withSpike = new LineString(new CoordinateArraySequence(coords), GEOMETRY_FACTORY);

        StartupElevationStabilizer.Result result = StartupElevationStabilizer.stabilize(withSpike);

        assertFalse(result.corrected());
        assertSame(withSpike, result.lineString());
        assertEquals(600.0, result.lineString().getCoordinateN(20).getZ(), 1e-12);
    }

    @Test
    void stabilizedGarminStartup_doesNotCreateLargeKalmanClimb() {
        LineString input = garminStartupLineString();
        StartupElevationStabilizer.Result result = StartupElevationStabilizer.stabilize(input);
        KalmanElevationSmoother smoother = new KalmanElevationSmoother(6.0, 10.0, 2.0, 0.5);

        LineString smoothed = smoother.denoise(result.lineString());

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < Math.min(20, smoothed.getNumPoints()); i++) {
            double z = smoothed.getCoordinateN(i).getZ();
            min = Math.min(min, z);
            max = Math.max(max, z);
        }
        assertTrue(max - min < 10.0, "Startup altitude should not create a multi-10m Kalman climb");
    }

    private static LineString garminStartupLineString() {
        return garminLikeLineString(24);
    }

    private static LineString track100210RawStartupLineString() {
        double baseTime = Instant.parse("2026-03-22T08:47:48Z").getEpochSecond();
        return makeLineStringWithBaseTime(baseTime, new double[][]{
                // Raw startup samples observed on track #100210:
                // 08:47:48 347.6m -> 08:47:49 456.0m = +108.4m/s.
                {8.5080764, 47.5583003, 347.6, 0},
                {8.5080630, 47.5583081, 456.0, 1},
                {8.5080558, 47.5583227, 464.8, 2},
                {8.5080300, 47.5583383, 471.8, 3},
                {8.5079745, 47.5583350, 473.2, 4},
                {8.5079412, 47.5583542, 473.4, 5},
                {8.5078986, 47.5583666, 473.4, 6},
                {8.5078842, 47.5583714, 473.8, 7},
                {8.5078751, 47.5583823, 474.4, 8},
                {8.5078636, 47.5583932, 474.4, 9},
                {8.5078705, 47.5584063, 474.4, 10},
                {8.5078797, 47.5584252, 474.4, 11},
                {8.5078926, 47.5584477, 474.6, 12},
                {8.5078972, 47.5584669, 474.6, 13},
                {8.5078978, 47.5584837, 474.6, 14},
                {8.5078979, 47.5584924, 474.6, 15},
                {8.5078980, 47.5585030, 474.6, 16},
                {8.5078982, 47.5585128, 474.4, 17},
                {8.5078985, 47.5585264, 474.4, 18},
                {8.5078988, 47.5585441, 474.2, 19},
                {8.5078990, 47.5585537, 474.2, 20},
                {8.5078994, 47.5585646, 474.2, 21},
                {8.5078997, 47.5585759, 474.2, 22},
                {8.5079000, 47.5585833, 474.2, 23}
        });
    }

    private static LineString garminLikeLineString(int count) {
        double[][] values = new double[count][4];
        double[] elevations = {347.6, 456.0, 464.8, 471.8, 473.2, 473.4, 473.4, 473.8, 474.4, 474.4};
        for (int i = 0; i < count; i++) {
            double z = i < elevations.length ? elevations[i] : 474.0 + (i % 2) * 0.2;
            values[i] = new double[]{8.5 + i * 0.00001, 47.4, z, 1000.0 + i};
        }
        return makeLineString(values);
    }

    private static LineString steadyLineString(int count, double startElevation, double elevationStep) {
        double[][] values = new double[count][4];
        for (int i = 0; i < count; i++) {
            values[i] = new double[]{
                    8.5 + i * 0.00001,
                    47.4,
                    startElevation + i * elevationStep,
                    1000.0 + i};
        }
        return makeLineString(values);
    }

    private static LineString makeLineString(double[][] values) {
        Coordinate[] coords = new Coordinate[values.length];
        for (int i = 0; i < values.length; i++) {
            coords[i] = new CoordinateXYZM(values[i][0], values[i][1], values[i][2], values[i][3]);
        }
        return new LineString(new CoordinateArraySequence(coords), GEOMETRY_FACTORY);
    }

    private static LineString makeLineStringWithBaseTime(double baseTime, double[][] values) {
        Coordinate[] coords = new Coordinate[values.length];
        for (int i = 0; i < values.length; i++) {
            coords[i] = new CoordinateXYZM(values[i][0], values[i][1], values[i][2], baseTime + values[i][3]);
        }
        return new LineString(new CoordinateArraySequence(coords), GEOMETRY_FACTORY);
    }

    private static Coordinate[] copyCoordinates(LineString lineString) {
        Coordinate[] coords = new Coordinate[lineString.getNumPoints()];
        for (int i = 0; i < lineString.getNumPoints(); i++) {
            Coordinate coordinate = lineString.getCoordinateN(i);
            coords[i] = new CoordinateXYZM(coordinate.getX(), coordinate.getY(), coordinate.getZ(), coordinate.getM());
        }
        return coords;
    }
}
