package com.x8ing.mtl.server.mtlserver.logic.motion;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentNotes;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackStopDetectorTest {

    private static final long START_MS = 1_700_000_000_000L;
    private static final Long TRACK_DATA_ID = 42L;
    private static final double METERS_PER_DEG_LON = 75_900.0;
    private static final double METERS_PER_DEG_LAT = 110_540.0;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    @Test
    void detectsDenseStopRangesWithPointAnchors() {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int t = 0; t <= 80; t += 10) {
            points.add(point((long) points.size() + 1, points.size(), t, t % 20, t % 10));
        }
        points.add(point(99L, points.size(), 90, 90, 0));

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);
        SegmentNotes notes = TrackStopDetector.summarizeStopRanges(ranges);

        assertEquals(1, ranges.size());
        assertEquals(1L, ranges.get(0).startPoint().getId());
        assertEquals(9L, ranges.get(0).endPoint().getId());
        assertEquals(80.0, ranges.get(0).durationInSec());
        assertEquals(1, notes.stopCount);
        assertEquals(80.0, notes.totalStoppedSec);
        assertEquals(80.0, notes.longestStopSec);
    }

    @Test
    void detectsSyntheticAnchorPairWithOnlyTwoPoints() {
        List<GpsTrackDataPoint> points = List.of(
                point(1L, 0, 0, 0, 0),
                point(2L, 1, 80, 0, 0)
        );

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(1, ranges.size());
        assertEquals(80.0, ranges.get(0).durationInSec());
    }

    @Test
    void ignoresSparseWindows() {
        List<GpsTrackDataPoint> points = List.of(
                point(1L, 0, 0, 0, 0),
                point(2L, 1, 90, 2, 0),
                point(3L, 2, 180, -2, 0),
                point(4L, 3, 270, 1, 0)
        );

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(0, ranges.size());
    }

    @Test
    void ignoresSlowLinearWalk() {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int t = 0; t <= 60; t++) {
            points.add(point((long) t + 1, t, t, t, 0));
        }

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(0, ranges.size());
    }

    @Test
    void doesNotDetectCurvedUphillAsStop() {
        double[][] offsets = {
                {0, 0},
                {8, 2},
                {-7, 5},
                {5, -6},
                {-4, -3},
                {6, 4},
                {-6, 1},
                {3, -7},
                {-2, 6},
                {7, -2},
                {-8, -1},
                {4, 5},
                {0, -4}
        };
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) {
            points.add(point(
                    (long) i + 1,
                    i,
                    i * 5,
                    offsets[i][0],
                    offsets[i][1],
                    400.0 + i * 2.0));
        }

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(0, ranges.size(), ranges.toString());
    }

    @Test
    void doesNotDetectFlatSwitchbackAsStop() {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int t = 0; t <= 80; t += 5) {
            int leg = t / 20;
            int withinLeg = t % 20;
            boolean eastbound = leg % 2 == 0;
            double x = eastbound ? withinLeg * 0.7 : 14.0 - withinLeg * 0.7;
            double y = leg * 2.0;
            points.add(point((long) points.size() + 1, points.size(), t, x, y, 100.0));
        }

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(0, ranges.size(), ranges.toString());
    }

    @Test
    void doesNotDetectSparseAlternatingSwitchbackAsStop() {
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
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) {
            points.add(point(
                    (long) i + 1,
                    i,
                    i * 30,
                    offsets[i][0],
                    offsets[i][1],
                    100.0));
        }

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(0, ranges.size(), ranges.toString());
    }

    @Test
    void dropsStandaloneMicroStopAfterConsolidation() {
        double[][] driftOffsets = {
                {0, 0}, {5, 3}, {-4, 6}, {-7, -2}, {8, -5}, {2, 10},
                {-10, 4}, {6, 8}, {-3, -8}, {9, 1}, {-6, 7}, {1, -10}
        };
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int i = 0; i < driftOffsets.length; i++) {
            points.add(point((long) i + 1, i, i * 5, driftOffsets[i][0], driftOffsets[i][1]));
        }
        points.add(point(100L, points.size(), 70, 0, 200));
        points.add(point(101L, points.size(), 80, 0, 400));

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(0, ranges.size(), ranges.toString());
    }

    @Test
    void detectsMessyRestaurantDriftAsBreak() {
        double[][] driftOffsets = {
                {0, 0}, {45, 15}, {-35, 25}, {60, -20}, {-55, -10}, {20, 55},
                {-15, 60}, {50, 35}, {-45, 45}, {30, -50}, {-60, 5}, {10, 40},
                {75, 15}, {-70, -25}, {35, 55}, {-25, 35}, {55, -35}, {-40, 10},
                {20, -45}, {-10, 50}, {45, -10}, {-35, -40}, {15, 30}, {-20, 20},
                {60, 20}, {-50, 35}, {25, -55}, {-15, -30}, {40, 45}, {-45, 0},
                {90, 0}
        };
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int i = 0; i < driftOffsets.length; i++) {
            points.add(point((long) i + 1, i, i * 10, driftOffsets[i][0], driftOffsets[i][1]));
        }
        points.add(point(100L, points.size(), 330, 0, 250));
        points.add(point(101L, points.size(), 350, 0, 450));

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(1, ranges.size(), ranges.toString());
        assertEquals(TrackStopDetector.StopCategory.BREAK, ranges.get(0).stopRange().category());
    }

    @Test
    void detectsDenseStopWhenOnlyThreePointSubwindowIsLinear() {
        double[][] offsets = {
                {0, 0},
                {2.5, 0},
                {5, 0},
                {0, 0},
                {1, 1},
                {-2, 1},
                {2, -1},
                {-1, -2},
                {1, -1},
                {-2, 2},
                {2, 1},
                {-1, 0},
                {1, 2}
        };
        int[] times = {0, 10, 20, 25, 30, 35, 40, 45, 50, 55, 60, 70, 80};
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) {
            points.add(point((long) i + 1, i, times[i], offsets[i][0], offsets[i][1], 100.0));
        }
        points.add(point(99L, points.size(), 90, 120, 0, 100.0));

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(1, ranges.size(), ranges.toString());
    }

    @Test
    void detectsBreakWhenSeedEndpointsAreOppositeSidesOfDriftCloud() {
        double[][] driftOffsets = {
                {-68, 0},
                {25, 20},
                {-25, -15},
                {45, -10},
                {-45, 20},
                {10, -55},
                {0, 45},
                {55, 5},
                {-55, -5},
                {30, 35},
                {-30, -35},
                {0, 0},
                {68, 0}
        };
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int i = 0; i < driftOffsets.length; i++) {
            points.add(point((long) i + 1, i, i * 15, driftOffsets[i][0], driftOffsets[i][1]));
        }
        points.add(point(100L, points.size(), 195, 0, 260));
        points.add(point(101L, points.size(), 210, 0, 420));

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(1, ranges.size());
        assertEquals(TrackStopDetector.StopCategory.BREAK, ranges.get(0).stopRange().category());
        assertEquals(180.0, ranges.get(0).durationInSec());
    }

    @Test
    void detectsNearbyRecordingGapAsLongBreak() {
        List<GpsTrackDataPoint> points = List.of(
                point(1L, 0, 0, 0, 0),
                point(2L, 1, 4_110, 23.8, 0)
        );

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(1, ranges.size());
        assertEquals(4_110.0, ranges.get(0).durationInSec());
        assertEquals(TrackStopDetector.StopCategory.LONG_BREAK, ranges.get(0).stopRange().category());
        assertEquals(TrackStopDetector.StopEvidence.RECORDING_GAP, ranges.get(0).stopRange().evidence());
    }

    @Test
    void doesNotDetectFarRecordingGapAsBreak() {
        List<GpsTrackDataPoint> points = List.of(
                point(1L, 0, 0, 0, 0),
                point(2L, 1, 4_110, 151, 0)
        );

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(0, ranges.size());
    }

    @Test
    void doesNotDetectFastRecordingGapAsBreak() {
        List<GpsTrackDataPoint> points = List.of(
                point(1L, 0, 0, 0, 0),
                point(2L, 1, 600, 100, 0)
        );

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(0, ranges.size());
    }

    @Test
    void doesNotDoubleCountExistingStopAnchorPairAsRecordingGap() {
        List<GpsTrackDataPoint> points = List.of(
                point(1L, 0, 0, 0, 0),
                point(2L, 1, 900, 0, 0)
        );

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.detectStopRangesInTrack(points);

        assertEquals(1, ranges.size());
        assertEquals(900.0, ranges.get(0).durationInSec());
        assertEquals(TrackStopDetector.StopEvidence.DENSE_CLUSTER, ranges.get(0).stopRange().evidence());
    }

    @Test
    void mapsStopRangeByTimestampWhenPersistedAnchorsMovedAwayFromCenter() {
        int longBreakDurationS = 3_944;
        List<GpsTrackDataPoint> points = List.of(
                point(1L, 0, 0, 180, 0),
                point(2L, 1, longBreakDurationS, -160, 0)
        );

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.mapStopRangesToTrackPoints(
                points,
                List.of(longBreakStopRange(0, 1, longBreakDurationS)));

        assertEquals(1, ranges.size());
        assertEquals(1L, ranges.get(0).startPoint().getId());
        assertEquals(2L, ranges.get(0).endPoint().getId());
        assertEquals(longBreakDurationS, ranges.get(0).durationInSec());
    }

    @Test
    void mapsStopRangeByTimestampWhenNeighboringPointsRemainNearCenter() {
        int longBreakDurationS = 3_944;
        List<GpsTrackDataPoint> points = List.of(
                point(1L, 0, 0, 180, 0),
                point(2L, 1, 1, 0, 0),
                point(3L, 2, longBreakDurationS - 1, 0, 0),
                point(4L, 3, longBreakDurationS, -160, 0)
        );

        List<TrackStopDetector.PointStopRange> ranges = TrackStopDetector.mapStopRangesToTrackPoints(
                points,
                List.of(longBreakStopRange(0, 3, longBreakDurationS)));

        assertEquals(1, ranges.size());
        assertEquals(1L, ranges.get(0).startPoint().getId());
        assertEquals(4L, ranges.get(0).endPoint().getId());
        assertEquals(longBreakDurationS, ranges.get(0).durationInSec());
    }

    private static TrackStopDetector.StopRange longBreakStopRange(int startIndex, int endIndex, int durationS) {
        return new TrackStopDetector.StopRange(
                startIndex,
                endIndex,
                START_MS / 1000.0,
                START_MS / 1000.0 + durationS,
                8.0,
                47.0,
                100.0,
                TrackStopDetector.StopCategory.LONG_BREAK,
                30.4,
                39.5,
                0.97,
                2_778,
                2_776);
    }

    private static GpsTrackDataPoint point(Long id, int pointIndex, int offsetSeconds, double xMeters, double yMeters) {
        GpsTrackDataPoint point = new GpsTrackDataPoint();
        point.setId(id);
        point.setGpsTrackDataId(TRACK_DATA_ID);
        point.setPointIndex(pointIndex);
        point.setPointTimestamp(new Date(START_MS + offsetSeconds * 1000L));
        point.setPointLongLat(GEOMETRY_FACTORY.createPoint(new Coordinate(
                8.0 + xMeters / METERS_PER_DEG_LON,
                47.0 + yMeters / METERS_PER_DEG_LAT)));
        return point;
    }

    private static GpsTrackDataPoint point(Long id,
                                           int pointIndex,
                                           int offsetSeconds,
                                           double xMeters,
                                           double yMeters,
                                           double elevationMeters) {
        GpsTrackDataPoint point = point(id, pointIndex, offsetSeconds, xMeters, yMeters);
        point.setPointAltitude(elevationMeters);
        return point;
    }
}
