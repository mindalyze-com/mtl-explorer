package com.x8ing.mtl.server.mtlserver.logic.motion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentNotes;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;

import java.util.*;

/**
 * Event adapter for the authoritative motion timeline. Detection never changes geometry.
 */
public final class TrackStopDetector {
    public static final double MIN_STOP_DURATION_S = TrackMotionAnalyzer.MIN_VISIBLE_BREAK_DURATION_S;
    private static final double BREAK_DURATION_S = 180;
    private static final double LEGACY_ANCHOR_RADIUS_M = 1;
    private static final double MILLIS_PER_SECOND = 1000;
    private static final double LONG_BREAK_DURATION_S = 600;

    private TrackStopDetector() {
    }

    public enum StopCategory {
        MICRO_STOP,
        SHORT_STOP,
        BREAK,
        LONG_BREAK
    }

    public enum StopEvidence {
        OBSERVED_STATIONARY,
        DENSE_CLUSTER,
        RECORDING_GAP
    }

    public interface StopDuration {
        double durationInSec();
    }

    @JsonPropertyOrder({
            "startIndex",
            "endIndex",
            "startTimeS",
            "endTimeS",
            "centerLng",
            "centerLat",
            "centerElevation",
            "category",
            "radiusR80M",
            "radiusR90M",
            "inlierRatio",
            "rawPoints",
            "deletedPoints",
            "evidence", "startBoundary", "endBoundary", "confirmedAtS"
    })
    public record StopRange(int startIndex,
                            int endIndex,
                            double startTimeS,
                            double endTimeS,
                            double centerLng,
                            double centerLat,
                            double centerElevation,
                            StopCategory category,
                            double radiusR80M,
                            double radiusR90M,
                            double inlierRatio,
                            int rawPoints,
                            int deletedPoints,
                            StopEvidence evidence,
                            TrackMotionAnalyzer.Boundary startBoundary,
                            TrackMotionAnalyzer.Boundary endBoundary,
                            Double confirmedAtS) implements StopDuration {
        public StopRange(int startIndex, int endIndex, double startTimeS, double endTimeS,
                         double centerLng, double centerLat, double centerElevation, StopCategory category,
                         double radiusR80M, double radiusR90M, double inlierRatio, int rawPoints, int deletedPoints,
                         StopEvidence evidence) {
            this(startIndex, endIndex, startTimeS, endTimeS, centerLng, centerLat, centerElevation, category,
                    radiusR80M, radiusR90M, inlierRatio, rawPoints, deletedPoints, evidence, null, null, null);
        }

        public StopRange(int startIndex,
                         int endIndex,
                         double startTimeS,
                         double endTimeS,
                         double centerLng,
                         double centerLat,
                         double centerElevation,
                         StopCategory category,
                         double radiusR80M,
                         double radiusR90M,
                         double inlierRatio,
                         int rawPoints,
                         int deletedPoints) {
            this(startIndex, endIndex, startTimeS, endTimeS, centerLng, centerLat, centerElevation,
                    category, radiusR80M, radiusR90M, inlierRatio, rawPoints, deletedPoints,
                    StopEvidence.DENSE_CLUSTER);
        }

        public StopRange {
            if (evidence == null) {
                evidence = StopEvidence.DENSE_CLUSTER;
            }
        }

        public double durationS() {
            return endTimeS - startTimeS;
        }

        @Override
        public double durationInSec() {
            return durationS();
        }
    }

    @JsonPropertyOrder({
            "startPoint",
            "endPoint",
            "stopRange"
    })
    public record PointStopRange(GpsTrackDataPoint startPoint,
                                 GpsTrackDataPoint endPoint,
                                 StopRange stopRange) implements StopDuration {
        @Override
        public double durationInSec() {
            return stopRange.durationInSec();
        }
    }

    public static SegmentNotes summarizeStopRanges(Collection<? extends StopDuration> ranges) {
        SegmentNotes notes = new SegmentNotes(0, 0.0, 0.0);
        if (ranges == null) {
            return notes;
        }
        for (StopDuration range : ranges) {
            double duration = range.durationInSec();
            notes.stopCount++;
            notes.totalStoppedSec += duration;
            if (duration > notes.longestStopSec) {
                notes.longestStopSec = duration;
            }
        }
        return notes;
    }

    public static List<StopRange> eventRanges(TrackMotionAnalyzer.Analysis analysis) {
        return analysis.breaks().stream().map(r -> new StopRange(r.startIndex(), r.endIndex(), r.startTimeS(), r.endTimeS(),
                r.centerLng(), r.centerLat(), r.centerElevation(), category(r.durationS()), 0, 0, 0,
                r.endIndex() - r.startIndex() + 1, 0, StopEvidence.OBSERVED_STATIONARY,
                r.startBoundary(), r.endBoundary(), r.confirmedAtS())).toList();
    }

    private static StopCategory category(double seconds) {
        return seconds >= LONG_BREAK_DURATION_S ? StopCategory.LONG_BREAK
                : seconds >= BREAK_DURATION_S ? StopCategory.BREAK : StopCategory.SHORT_STOP;
    }

    public static List<StopRange> detectStopRangesInCoordinates(List<Coordinate> coordinates) {
        return eventRanges(TrackMotionAnalyzer.analyze(coordinates, List.of()));
    }

    public static List<StopRange> detectRawStopRangesInCoordinates(List<Coordinate> coordinates) {
        return detectStopRangesInCoordinates(coordinates);
    }

    static List<PointStopRange> detectStopRangesInTrack(List<GpsTrackDataPoint> points) {
        if (points == null) return List.of();
        List<Coordinate> coordinates = points.stream().map(p -> (Coordinate) new CoordinateXYZM(
                p.getPointLongLat().getX(), p.getPointLongLat().getY(),
                p.getPointAltitude() == null ? Double.NaN : p.getPointAltitude(),
                p.getPointTimestamp() == null ? Double.NaN : p.getPointTimestamp().getTime() / MILLIS_PER_SECOND)).toList();
        List<Integer> breaks = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).getIntervalStatus() == GpsTrackDataPoint.IntervalStatus.UNKNOWN) breaks.add(i);
        }
        List<PointStopRange> ranges = new ArrayList<>();
        for (StopRange range : eventRanges(TrackMotionAnalyzer.analyze(coordinates, breaks))) {
            ranges.add(new PointStopRange(points.get(range.startIndex()), points.get(range.endIndex()), range));
        }
        return ranges;
    }

    /**
     * Ranges address original canonical indexes, never nearest coordinates or timestamps.
     */
    public static List<PointStopRange> mapStopRangesToTrackPoints(List<GpsTrackDataPoint> points, List<StopRange> ranges) {
        if (points == null || ranges == null) return List.of();
        Map<Integer, GpsTrackDataPoint> byIndex = new HashMap<>();
        points.forEach(p -> byIndex.put(p.getPointIndex(), p));
        List<PointStopRange> result = new ArrayList<>();
        for (StopRange range : ranges) {
            GpsTrackDataPoint start = byIndex.get(range.startIndex()), end = byIndex.get(range.endIndex());
            if (start != null && end != null && start != end) result.add(new PointStopRange(start, end, range));
        }
        return result;
    }

    /**
     * Legacy geometry utility for displaying previously collapsed tracks; never motion evidence.
     */
    public static boolean isStopAnchorPair(Coordinate a, Coordinate b) {
        return a != null && b != null && Double.isFinite(a.getM()) && Double.isFinite(b.getM())
               && b.getM() - a.getM() >= MIN_STOP_DURATION_S && GPXReader.getDistanceBetweenTwoWGS84(a, b) <= LEGACY_ANCHOR_RADIUS_M;
    }
}
