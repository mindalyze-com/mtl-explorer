package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingContinuityTest {
    @Test
    void storedBoundaryResetsKnownTimeBeforeUntimedFixes() {
        var points = List.of(point(0, 0d), point(1, null), point(2, 3600d));
        points.get(1).setIntervalStatus(GpsTrackDataPoint.IntervalStatus.UNKNOWN);
        assertTrue(RecordingContinuity.timeBreakPointIndexes(points).isEmpty());
    }

    @Test
    void eventBoundaryResetsKnownTimeBeforeUntimedFixes() {
        var points = List.of(point(0, 0d), point(1, null), point(2, 3600d));
        var gap = new GpsTrackEvent();
        gap.setEventType(GpsTrackEvent.EVENT_TYPE.GPS_GAP);
        gap.setEndPointIndex(1);
        assertTrue(RecordingContinuity.timeBreakPointIndexes(points, List.of(gap)).isEmpty());
    }

    @Test
    void untimedFixCannotHideLongGapWithoutAnExplicitBoundary() {
        assertEquals(Set.of(2), RecordingContinuity.timeBreakPointIndexes(
                List.of(point(0, 0d), point(1, null), point(2, 3600d))));
    }

    private static GpsTrackDataPoint point(int index, Double seconds) {
        var point = new GpsTrackDataPoint();
        point.setPointIndex(index);
        point.setPointLongLat(new GeometryFactory().createPoint(new Coordinate(index / 100000d, 0)));
        point.setPointTimestamp(seconds == null ? null : new Date(Math.round(seconds * 1000)));
        point.setIntervalStatus(index == 0 ? GpsTrackDataPoint.IntervalStatus.START : GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY);
        return point;
    }
}
