package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentTimingStatus;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

final class SegmentAnalysisFixtures {
    static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    static GpsTrackDataPoint point(int index, double seconds, double meters) {
        var p = new GpsTrackDataPoint();
        p.setId((long) index + 10);
        p.setPointIndex(index);
        p.setGpsTrackDataId(2L);
        p.setPointTimestamp(Date.from(time(seconds)));
        p.setDurationSinceStart(seconds);
        p.setDistanceInMeterSinceStart(meters);
        p.setPointLongLat(new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(meters / 111_320, 0)));
        p.setIntervalStatus(index == 0 ? GpsTrackDataPoint.IntervalStatus.START : GpsTrackDataPoint.IntervalStatus.OBSERVED);
        p.setPointAltitude(500 + index * 10d);
        p.setAscentInMeterSinceStart(index * 10d);
        p.setDescentInMeterSinceStart(0d);
        p.setSpeedInKmhMovingWindow(36d);
        p.setSlopePercentageInMovingWindow(1d);
        return p;
    }

    static Instant time(double seconds) {
        return EPOCH.plusMillis(Math.round(seconds * 1000));
    }

    static SegmentAttemptResolver.ResolvedAttempt attempt(List<GpsTrackDataPoint> points, double startPosition, double endPosition,
                                                          double startTime, double endTime, Double distance) {
        var track = new GpsTrack();
        track.setId(1L);
        var identity = new SegmentAttemptIdentity("attempt", 1, "A", 1, "B", 1, time(startTime), time(endTime), startPosition, endPosition,
                coordinate(points, startPosition), coordinate(points, endPosition), "revision", "zones", SegmentTimingStatus.AVAILABLE);
        var summary = new SegmentAttemptSummary(identity, distance, endTime - startTime,
                distance == null || endTime == startTime ? null : distance / (endTime - startTime) * 3.6,
                null, null, null, null, Map.of());
        return new SegmentAttemptResolver.ResolvedAttempt(summary, track, points, List.of());
    }

    private static List<Double> coordinate(List<GpsTrackDataPoint> points, double position) {
        int left = Math.min((int) position, points.size() - 1), right = Math.min(left + 1, points.size() - 1);
        double from = points.get(left).getPointLongLat().getX(), to = points.get(right).getPointLongLat().getX();
        return List.of(from + (to - from) * (position - left), 0d);
    }

    static SegmentCoreAnalysis core(double duration, double distance, double[][] progress) {
        var identity = new SegmentAttemptIdentity("attempt-" + duration, 1, "A", 1, "B", 1, time(0), time(duration), 0d, 1d,
                List.of(0d, 0d), List.of(0.01, 0d), "revision", "zones", SegmentTimingStatus.AVAILABLE);
        var summary = new SegmentAttemptSummary(identity, distance, duration, distance / duration * 3.6, null, null, null, null, Map.of());
        var geometry = Arrays.stream(progress).map(p -> new SegmentGeometryPoint(p[0], p[1], time(p[1]), p[0] / 111_320, 0, p.length > 2 && p[2] == 1)).toList();
        return new SegmentCoreAnalysis(identity, summary, List.of(), geometry, geometry.size());
    }
}
