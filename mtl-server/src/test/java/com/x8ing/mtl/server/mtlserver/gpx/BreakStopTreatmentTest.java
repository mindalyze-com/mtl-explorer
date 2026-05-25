package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.logic.motion.TrackStopDetector;
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

    /** About 111.32 m per 0.001 degree latitude near the equator. */
    private static final double M_PER_DEG_LAT = 111_320.0;

    private static Coordinate at(double lng, double lat, double t) {
        return new CoordinateXYZM(lng, lat, 100.0, t);
    }

    /** Convert a north offset in meters to a latitude delta near (lat=0). */
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

    // stop collapse

    @Test
    void dropsStandaloneMicroStopAfterConsolidation() {
        // 12 drift points scribbled within ~15 m radius over 60 s, then
        // movement resumes 200 m away.
        List<Coordinate> in = new ArrayList<>();
        double[][] driftOffsets = {
                {0, 0}, {5, 3}, {-4, 6}, {-7, -2}, {8, -5}, {2, 10},
                {-10, 4}, {6, 8}, {-3, -8}, {9, 1}, {-6, 7}, {1, -10}
        };
        for (int i = 0; i < driftOffsets.length; i++) {
            double dxM = driftOffsets[i][0];
            double dyM = driftOffsets[i][1];
            in.add(at(dxM / M_PER_DEG_LAT, dyM / M_PER_DEG_LAT, i * 5));
        }
        // Departure
        in.add(at(0.0, latOffset(200), 70));
        in.add(at(0.0, latOffset(400), 80));

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);
        assertEquals(0, r.stops().size(), "standalone micro stops are not returned");
        assertEquals(0, r.collapsedPoints());
        assertEquals(in.size(), r.cleanedCoordinates().size());
    }

    @Test
    void collapsesMessyIndoorDriftBeyondStrictRadius() {
        // 5 minutes of stationary indoor GPS drift: most points stay within
        // roughly 60 m of the robust center, with a few larger excursions.
        List<Coordinate> in = new ArrayList<>();
        double[][] driftOffsets = {
                {0, 0}, {45, 15}, {-35, 25}, {60, -20}, {-55, -10}, {20, 55},
                {-15, 60}, {50, 35}, {-45, 45}, {30, -50}, {-60, 5}, {10, 40},
                {75, 15}, {-70, -25}, {35, 55}, {-25, 35}, {55, -35}, {-40, 10},
                {20, -45}, {-10, 50}, {45, -10}, {-35, -40}, {15, 30}, {-20, 20},
                {60, 20}, {-50, 35}, {25, -55}, {-15, -30}, {40, 45}, {-45, 0},
                {90, 0} // tolerated outlier inside the stop cloud
        };
        for (int i = 0; i < driftOffsets.length; i++) {
            in.add(at(driftOffsets[i][0] / M_PER_DEG_LAT, driftOffsets[i][1] / M_PER_DEG_LAT, i * 10));
        }
        in.add(at(0.0, latOffset(250), 330));
        in.add(at(0.0, latOffset(450), 350));

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);

        assertEquals(1, r.stops().size(), "messy restaurant drift should collapse as one stop");
        assertEquals(TrackStopDetector.StopCategory.BREAK, r.stops().get(0).category());
        assertEquals(4, r.cleanedCoordinates().size());
    }

    @Test
    void consolidatesRestaurantStayAcrossTemporaryGpsDropout() {
        List<Coordinate> in = new ArrayList<>();
        double[][] firstDrift = {
                {0, 0}, {42, 12}, {-38, 20}, {58, -18}, {-52, -8}, {18, 50},
                {-14, 55}, {48, 32}, {-42, 42}, {28, -48}, {-58, 4}, {8, 38}, {68, 12}
        };
        for (int i = 0; i < firstDrift.length; i++) {
            in.add(at(firstDrift[i][0] / M_PER_DEG_LAT, latOffset(firstDrift[i][1]), i * 10));
        }

        double[][] secondDrift = {
                {-65, -20}, {32, 52}, {-22, 34}, {52, -32}, {-36, 12}, {18, -42},
                {-8, 48}, {42, -8}, {-32, -38}, {12, 28}, {-18, 18}, {56, 18}, {-46, 32}
        };
        for (int i = 0; i < secondDrift.length; i++) {
            in.add(at(secondDrift[i][0] / M_PER_DEG_LAT, latOffset(secondDrift[i][1]), 300 + i * 10));
        }
        in.add(at(0.0, latOffset(260), 480));

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);

        assertEquals(1, r.stops().size(), "dropout inside a restaurant stop should not split the stay");
        assertEquals(420.0, r.stops().get(0).durationS());
        assertEquals(3, r.cleanedCoordinates().size());
    }

    @Test
    void doesNotCollapseSlowLinearWalk() {
        // 1 m/s walk north for 60 s. The linearity guard keeps compact
        // short windows of real movement from being collapsed as a stop.
        List<Coordinate> in = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            in.add(at(0.0, latOffset(i), i));
        }
        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);
        assertEquals(0, r.stops().size(), "linear walk must not be detected as a stop");
        assertEquals(in.size(), r.cleanedCoordinates().size());
    }

    @Test
    void doesNotCollapseShortStop() {
        // 20 s of stillness, below MIN_STOP_DURATION_S (30 s). Not collapsed.
        List<Coordinate> in = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            in.add(at(0.0, latOffset(i * 0.5), i * 5)); // tiny jitter
        }
        in.add(at(0.0, latOffset(200), 25));

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);
        assertEquals(0, r.stops().size());
        assertEquals(in.size(), r.cleanedCoordinates().size());
    }

    @Test
    void doesNotTreatSparseRepeatedRawFixesAsStopAnchors() {
        List<Coordinate> in = List.of(
                at(0.0, 0.0, 0),
                at(0.0, 0.0, 80),
                at(0.0, latOffset(200), 100)
        );

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);

        assertEquals(0, r.stops().size(), "two sparse raw fixes are not dense stop evidence");
        assertEquals(in.size(), r.cleanedCoordinates().size());
    }

    @Test
    void hysteresisAbsorbsSingleJumpInsideStop() {
        // Drift cluster with ONE spike-like 90 m jump and immediate return:
        // the inner-spike pass may not catch it (e.g. dtAC > spike window or
        // the gap inside the cluster), but the stop hysteresis must absorb it.
        List<Coordinate> in = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            // tiny drift around origin every 5 s
            double dx = ((i % 3) - 1) * 3.0;
            double dy = ((i % 4) - 2) * 4.0;
            in.add(at(dx / M_PER_DEG_LAT, dy / M_PER_DEG_LAT, i * 5));
        }
        // single 90 m jump (escape would need >=30 s + >=3 points to confirm)
        in.add(at(0.0, latOffset(90), 50));
        // back near origin
        for (int i = 11; i < 16; i++) {
            in.add(at(0.0, latOffset(2), i * 5));
        }
        // departure
        in.add(at(0.0, latOffset(500), 100));

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);
        assertEquals(1, r.stops().size(), "drift with one absorbed jump should be ONE stop");
    }

    @Test
    void confirmedEscapeEndsStop() {
        // Drift for 60 s, then sustained movement away for >30 s: stop must
        // end at the first sustained-escape point.
        List<Coordinate> in = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            double dx = ((i % 3) - 1) * 3.0;
            double dy = ((i % 4) - 2) * 4.0;
            in.add(at(dx / M_PER_DEG_LAT, dy / M_PER_DEG_LAT, i * 5));
        }
        // 10 m/s movement away: 4 points, 30 s, well outside EXIT_RADIUS_M.
        for (int i = 13; i < 17; i++) {
            in.add(at(0.0, latOffset((i - 12) * 200), i * 5));
        }

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);
        assertEquals(1, r.stops().size());
        // anchors + 4 movement points
        assertEquals(2 + 4, r.cleanedCoordinates().size());
    }

    @Test
    void escapePeekDoesNotAbsorbAcrossLargeGap() {
        List<Coordinate> in = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            in.add(at(0.0, latOffset((i % 3) * 2), i * 5));
        }
        in.add(at(0.0, latOffset(160), 70));
        in.add(at(0.0, latOffset(2), 8 * 60));

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);

        assertEquals(1, r.stops().size());
        assertTrue(r.stops().get(0).endTimeS() < 65.0);
    }

    @Test
    void mergesNearbyStopFragmentsSeparatedByShortDrift() {
        List<Coordinate> in = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            in.add(at(0.0, latOffset((i % 3) * 2), i * 5));
        }
        in.add(at(0.0, latOffset(70), 65));
        in.add(at(0.0, latOffset(55), 75));
        for (int i = 0; i < 12; i++) {
            in.add(at(0.0, latOffset(45 + (i % 3) * 2), 85 + i * 5));
        }
        in.add(at(0.0, latOffset(250), 155));

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);

        assertEquals(1, r.stops().size());
        assertEquals(3, r.cleanedCoordinates().size());
    }

    @Test
    void keepsRealMovementBetweenNearbyStops() {
        List<Coordinate> in = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            in.add(at(0.0, 0.0, i * 5));
        }
        in.add(at(0.0, latOffset(30), 70));
        in.add(at(0.0, latOffset(60), 80));
        for (int i = 0; i < 13; i++) {
            in.add(at(0.0, latOffset(90), 90 + i * 5));
        }

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);

        assertEquals(2, r.stops().size(), "real movement must keep the two stops separate");
        assertEquals(6, r.cleanedCoordinates().size());
        assertEquals(latOffset(30), r.cleanedCoordinates().get(2).getY(), 1e-12);
        assertEquals(latOffset(60), r.cleanedCoordinates().get(3).getY(), 1e-12);
    }

    @Test
    void consolidatesRestaurantFragmentsSeparatedByDirectionalGpsCreep() {
        List<Coordinate> in = new ArrayList<>();
        double[][] firstDrift = {
                {0, 0}, {4, 2}, {-3, 5}, {-5, -2}, {7, -3}, {2, 8},
                {-8, 3}, {5, 6}, {-2, -6}, {8, 1}, {-5, 5}, {1, -8}
        };
        for (int i = 0; i < firstDrift.length; i++) {
            in.add(at(firstDrift[i][0] / M_PER_DEG_LAT, latOffset(firstDrift[i][1]), i * 5));
        }
        for (int i = 0; i < 9; i++) {
            double xM = i * 3.5;
            double yM = i % 2 == 0 ? 1.5 : -1.5;
            in.add(at(xM / M_PER_DEG_LAT, latOffset(yM), 60 + i * 5));
        }
        double[][] secondDrift = {
                {28, 0}, {31, 3}, {25, 5}, {22, -2}, {34, -3}, {30, 8},
                {20, 3}, {33, 6}, {26, -6}, {35, 1}, {23, 5}, {29, -8}
        };
        for (int i = 0; i < secondDrift.length; i++) {
            in.add(at(secondDrift[i][0] / M_PER_DEG_LAT, latOffset(secondDrift[i][1]), 105 + i * 5));
        }
        in.add(at(0.0, latOffset(250), 170));

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);

        assertEquals(1, r.stops().size(), "stationary GPS creep should consolidate into one stop");
        assertEquals(TrackStopDetector.StopCategory.SHORT_STOP, r.stops().get(0).category());
        assertEquals(3, r.cleanedCoordinates().size());
    }

    @Test
    void doesNotConsolidateStopsAcrossSwitchbackMovement() {
        List<Coordinate> in = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            in.add(at(0.0, latOffset((i % 3) * 2), i * 5));
        }
        double[][] bridge = {
                {11, 0},
                {22, 0},
                {33, 0},
                {45, 0},
                {45, 15},
                {45, 30}
        };
        for (int i = 0; i < bridge.length; i++) {
            in.add(at(bridge[i][0] / M_PER_DEG_LAT, latOffset(bridge[i][1]), 65 + i * 10));
        }
        for (int i = 0; i < 12; i++) {
            in.add(at(45.0 / M_PER_DEG_LAT, latOffset(30 + (i % 3) * 2), 125 + i * 5));
        }

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);

        assertEquals(2, r.stops().size(), "switchback movement must keep nearby stops separate");
    }

    @Test
    void doesNotCollapseSparseAlternatingSwitchback() {
        double[][] offsets = {
                {0, 0},
                {45, 4},
                {0, 8},
                {45, 12},
                {0, 16},
                {45, 20},
                {0, 24},
                {45, 28}
        };
        List<Coordinate> in = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) {
            in.add(at(offsets[i][0] / M_PER_DEG_LAT, latOffset(offsets[i][1]), i * 30));
        }

        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);

        assertEquals(0, r.stops().size(), "sparse switchbacks must not collapse as stops");
        assertEquals(in.size(), r.cleanedCoordinates().size());
    }

    // edge cases

    @Test
    void emptyAndTinyInputIsPassThrough() {
        BreakStopTreatment.Result rEmpty = BreakStopTreatment.apply(List.of());
        assertEquals(0, rEmpty.cleanedCoordinates().size());

        List<Coordinate> tiny = List.of(at(0, 0, 0), at(0, latOffset(1), 1));
        BreakStopTreatment.Result rTiny = BreakStopTreatment.apply(tiny);
        assertEquals(2, rTiny.cleanedCoordinates().size());
    }

    @Test
    void veryLongStopOver11hIsCollapsed() {
        // 12 h of stillness with sparse but regular samples should still be cleaned.
        // The collapsed same-location anchors represent one long stop and are protected
        // from temporal splitting; noisy stationary geometry must not survive.
        List<Coordinate> in = new ArrayList<>();
        int n = 60; // 60 samples over 12 h, about one every 12 minutes
        double durationS = 12 * 3600.0;
        for (int i = 0; i < n; i++) {
            double t = i * (durationS / (n - 1));
            in.add(at(0.0, latOffset(2), t));
        }
        BreakStopTreatment.Result r = BreakStopTreatment.apply(in);
        assertEquals(1, r.stops().size());
        assertEquals(2, r.cleanedCoordinates().size());
        assertEquals(1, GPXReader.splitByTemporalGaps(r.cleanedCoordinates(), r.stops()).size());
    }
}
