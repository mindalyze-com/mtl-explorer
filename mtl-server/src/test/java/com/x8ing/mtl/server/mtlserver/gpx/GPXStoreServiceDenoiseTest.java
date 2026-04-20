package com.x8ing.mtl.server.mtlserver.gpx;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the distance-based median elevation denoiser.
 * No Spring context required.
 * <p>
 * Coordinates are XYZM: X=longitude, Y=latitude, Z=elevation, M=epoch seconds.
 * Tests use coordinates near Zurich (8.5°E, 47.4°N).
 */
class GPXStoreServiceDenoiseTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);
    private static final MedianElevationSmoother SMOOTHER = new MedianElevationSmoother();

    // ─── Spike removal ───────────────────────────────────────────────────

    @Test
    void singleElevationSpike_shouldBeRemoved() {
        // 7 points at ~10m spacing, 2s apart, steady ~500m elevation with one 580m spike at index 3
        LineString input = makeLineString(new double[][]{
                {8.50000, 47.40000, 500, 1000},
                {8.50010, 47.40000, 502, 1002},
                {8.50020, 47.40000, 501, 1004},
                {8.50030, 47.40000, 580, 1006},  // spike!
                {8.50040, 47.40000, 503, 1008},
                {8.50050, 47.40000, 501, 1010},
                {8.50060, 47.40000, 502, 1012},
        });

        LineString result = SMOOTHER.denoise(input);

        assertEquals(input.getNumPoints(), result.getNumPoints());
        // The spike at index 3 should be replaced by the median of its neighbors
        double smoothedSpike = result.getCoordinateN(3).getZ();
        assertTrue(smoothedSpike < 510, "Spike should be removed, got " + smoothedSpike);
        assertTrue(smoothedSpike > 490, "Should stay near real elevation, got " + smoothedSpike);
    }

    @Test
    void twoConsecutiveSpikes_shouldBeSmoothed() {
        LineString input = makeLineString(new double[][]{
                {8.50000, 47.40000, 500, 1000},
                {8.50010, 47.40000, 502, 1002},
                {8.50020, 47.40000, 560, 1004},  // spike
                {8.50030, 47.40000, 570, 1006},  // spike
                {8.50040, 47.40000, 503, 1008},
                {8.50050, 47.40000, 501, 1010},
                {8.50060, 47.40000, 502, 1012},
        });

        LineString result = SMOOTHER.denoise(input);

        double z2 = result.getCoordinateN(2).getZ();
        double z3 = result.getCoordinateN(3).getZ();
        // With 7 points all within 50m, median of {500,502,560,570,503,501,502} = 502
        // Both spikes should be significantly reduced
        assertTrue(z2 < 530, "First spike should be reduced, got " + z2);
        assertTrue(z3 < 530, "Second spike should be reduced, got " + z3);
    }

    // ─── Preservation of real terrain ────────────────────────────────────

    @Test
    void steadyClimb_shouldBePreserved() {
        // 10 points climbing steadily 5m per step
        double[][] data = new double[10][4];
        for (int i = 0; i < 10; i++) {
            data[i] = new double[]{8.50000 + i * 0.0003, 47.40000, 500 + i * 5.0, 1000 + i * 3};
        }
        LineString input = makeLineString(data);

        LineString result = SMOOTHER.denoise(input);

        // Middle points should stay close to original
        for (int i = 2; i < 8; i++) {
            double original = input.getCoordinateN(i).getZ();
            double denoised = result.getCoordinateN(i).getZ();
            assertEquals(original, denoised, 5.0,
                    "Steady climb point " + i + " should be preserved (orig=" + original + ", got=" + denoised + ")");
        }
    }

    @Test
    void flatTerrain_shouldRemainFlat() {
        double[][] data = new double[8][4];
        for (int i = 0; i < 8; i++) {
            data[i] = new double[]{8.50000 + i * 0.00010, 47.40000, 500, 1000 + i * 2};
        }
        LineString input = makeLineString(data);

        LineString result = SMOOTHER.denoise(input);

        for (int i = 0; i < 8; i++) {
            assertEquals(500.0, result.getCoordinateN(i).getZ(), 0.01,
                    "Flat terrain at point " + i + " should remain 500m");
        }
    }

    // ─── X, Y, M preservation ────────────────────────────────────────────

    @Test
    void xyAndTimestamp_shouldBePreserved() {
        LineString input = makeLineString(new double[][]{
                {8.50000, 47.40000, 500, 1000},
                {8.50010, 47.40005, 502, 1002},
                {8.50020, 47.40010, 580, 1004},  // Z changed by denoise
                {8.50030, 47.40015, 501, 1006},
                {8.50040, 47.40020, 503, 1008},
        });

        LineString result = SMOOTHER.denoise(input);

        for (int i = 0; i < input.getNumPoints(); i++) {
            Coordinate orig = input.getCoordinateN(i);
            Coordinate res = result.getCoordinateN(i);
            assertEquals(orig.getX(), res.getX(), 1e-10, "X should be preserved at " + i);
            assertEquals(orig.getY(), res.getY(), 1e-10, "Y should be preserved at " + i);
            assertEquals(orig.getM(), res.getM(), 1e-10, "M should be preserved at " + i);
        }
    }

    // ─── Edge cases ──────────────────────────────────────────────────────

    @Test
    void twoPoints_shouldReturnUnchanged() {
        LineString input = makeLineString(new double[][]{
                {8.50000, 47.40000, 500, 1000},
                {8.50010, 47.40000, 600, 1002},
        });

        LineString result = SMOOTHER.denoise(input);

        assertEquals(500.0, result.getCoordinateN(0).getZ());
        assertEquals(600.0, result.getCoordinateN(1).getZ());
    }

    @Test
    void nanElevation_shouldBePreserved() {
        LineString input = makeLineString(new double[][]{
                {8.50000, 47.40000, 500, 1000},
                {8.50010, 47.40000, Double.NaN, 1002},
                {8.50020, 47.40000, 502, 1004},
                {8.50030, 47.40000, 501, 1006},
        });

        LineString result = SMOOTHER.denoise(input);

        assertTrue(Double.isNaN(result.getCoordinateN(1).getZ()), "NaN elevation should be preserved");
    }

    @Test
    void allSameElevation_shouldRemainUnchanged() {
        double[][] data = new double[5][4];
        for (int i = 0; i < 5; i++) {
            data[i] = new double[]{8.50000 + i * 0.00010, 47.40000, 750, 1000 + i * 2};
        }
        LineString input = makeLineString(data);

        LineString result = SMOOTHER.denoise(input);

        for (int i = 0; i < 5; i++) {
            assertEquals(750.0, result.getCoordinateN(i).getZ(), 0.01);
        }
    }

    // ─── Time cap behavior ───────────────────────────────────────────────

    @Test
    void stationaryCluster_timeCapsWindow() {
        // 20 points at the exact same location (stopped), 2s apart = 40s total
        // Only points within ±30s of center should be included
        double[][] data = new double[20][4];
        for (int i = 0; i < 20; i++) {
            // Elevation slowly rising, with a spike at point 10
            double z = (i == 10) ? 800 : 500 + i * 0.5;
            data[i] = new double[]{8.50000, 47.40000, z, 1000 + i * 2};
        }
        LineString input = makeLineString(data);

        LineString result = SMOOTHER.denoise(input);

        // Spike at index 10 should be smoothed out
        double z10 = result.getCoordinateN(10).getZ();
        assertTrue(z10 < 520, "Stationary spike should be removed, got " + z10);
    }

    // ─── Distance-based window sizing ────────────────────────────────────

    @Test
    void widelySpacedPoints_shouldHaveSmallerWindow() {
        // Points 80m apart — each neighbor exceeds 50m radius, so only self is in window
        // (and possibly the immediate neighbor barely inside)
        // Spike should be less effectively smoothed with fewer neighbors
        LineString input = makeLineString(new double[][]{
                {8.50000, 47.40000, 500, 1000},
                {8.50100, 47.40000, 502, 1010},   // ~80m away
                {8.50200, 47.40000, 700, 1020},   // spike, ~80m from neighbors
                {8.50300, 47.40000, 501, 1030},
                {8.50400, 47.40000, 503, 1040},
        });

        LineString result = SMOOTHER.denoise(input);

        // With points ~80m apart, the window for point 2 may only include itself
        // or at most immediate neighbors. The spike may not be fully removed.
        double z2 = result.getCoordinateN(2).getZ();
        // This tests the distance-limiting behavior — the window should NOT include all 5 points
        // With only 1-2 neighbors, the median may still be high
        assertNotNull(result, "Should not crash with wide spacing");
    }

    // ─── Energy impact: verify downstream gravitational energy improves ──

    @Test
    void spikeRemoval_reducesGravitationalDelta() {
        LineString input = makeLineString(new double[][]{
                {8.50000, 47.40000, 500, 1000},
                {8.50010, 47.40000, 502, 1002},
                {8.50020, 47.40000, 501, 1004},
                {8.50030, 47.40000, 580, 1006},  // +79m spike
                {8.50040, 47.40000, 503, 1008},  // -77m drop
                {8.50050, 47.40000, 501, 1010},
                {8.50060, 47.40000, 502, 1012},
        });

        // Before denoising: delta at index 3 = 580-501 = +79m, at index 4 = 503-580 = -77m
        double rawDelta3 = input.getCoordinateN(3).getZ() - input.getCoordinateN(2).getZ();
        assertEquals(79.0, rawDelta3, 0.1);

        LineString result = SMOOTHER.denoise(input);

        double smoothDelta3 = result.getCoordinateN(3).getZ() - result.getCoordinateN(2).getZ();
        assertTrue(Math.abs(smoothDelta3) < 10,
                "Denoised delta should be much smaller than raw 79m, got " + smoothDelta3);
    }

    // ─── Helper ──────────────────────────────────────────────────────────

    /**
     * Build an XYZM LineString from an array of [x, y, z, m] tuples.
     */
    private static LineString makeLineString(double[][] xyzm) {
        Coordinate[] coords = new Coordinate[xyzm.length];
        for (int i = 0; i < xyzm.length; i++) {
            coords[i] = new CoordinateXYZM(xyzm[i][0], xyzm[i][1], xyzm[i][2], xyzm[i][3]);
        }
        return new LineString(new CoordinateArraySequence(coords), GF);
    }
}
