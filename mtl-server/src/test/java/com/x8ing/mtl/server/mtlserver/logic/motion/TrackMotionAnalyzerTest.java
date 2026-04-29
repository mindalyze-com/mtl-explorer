package com.x8ing.mtl.server.mtlserver.logic.motion;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentNotes;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackMotionAnalyzerTest {

    private static final long START_MS = 1_700_000_000_000L;
    private static final Long TRACK_DATA_ID = 42L;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    @Test
    void detectsStopRangesWithPointAnchors() {
        List<GpsTrackDataPoint> points = List.of(
                point(1L, 0, 0, 0),
                point(2L, 1, 10, 0),
                point(3L, 2, 40, 1),
                point(4L, 3, 80, 2),
                point(5L, 4, 90, 20)
        );

        List<TrackMotionAnalyzer.StopRange> ranges = TrackMotionAnalyzer.detectStopRangesInTrack(points);
        SegmentNotes notes = TrackMotionAnalyzer.summarizeStopRanges(ranges);

        assertEquals(1, ranges.size());
        assertEquals(1L, ranges.get(0).startPoint().getId());
        assertEquals(4L, ranges.get(0).endPoint().getId());
        assertEquals(80.0, ranges.get(0).durationInSec());
        assertEquals(1, notes.stopCount);
        assertEquals(80.0, notes.totalStoppedSec);
        assertEquals(80.0, notes.longestStopSec);
    }

    @Test
    void ignoresLowSpeedRunsShorterThanMinimumStopDuration() {
        List<GpsTrackDataPoint> points = List.of(
                point(1L, 0, 0, 0),
                point(2L, 1, 10, 0),
                point(3L, 2, 29, 1),
                point(4L, 3, 40, 20)
        );

        List<TrackMotionAnalyzer.StopRange> ranges = TrackMotionAnalyzer.detectStopRangesInTrack(points);
        SegmentNotes notes = TrackMotionAnalyzer.summarizeStopRanges(ranges);

        assertEquals(0, ranges.size());
        assertEquals(0, notes.stopCount);
        assertEquals(0.0, notes.totalStoppedSec);
        assertEquals(0.0, notes.longestStopSec);
    }

    private static GpsTrackDataPoint point(Long id, int pointIndex, int offsetSeconds, double distanceMeters) {
        GpsTrackDataPoint point = new GpsTrackDataPoint();
        point.setId(id);
        point.setGpsTrackDataId(TRACK_DATA_ID);
        point.setPointIndex(pointIndex);
        point.setPointTimestamp(new Date(START_MS + offsetSeconds * 1000L));
        point.setDistanceInMeterSinceStart(distanceMeters);
        point.setPointLongLat(GEOMETRY_FACTORY.createPoint(new Coordinate(8.0 + pointIndex * 0.00001, 47.0)));
        return point;
    }
}
