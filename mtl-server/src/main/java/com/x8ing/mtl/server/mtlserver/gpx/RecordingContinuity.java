package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;

import java.util.Collection;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Limits unsupported interpolation while retaining the recorded activity as one track.
 */
public final class RecordingContinuity {
    public static final double MAX_INTERPOLATION_GAP_SECONDS = 120.0;
    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final double MAX_UNTIMED_SPATIAL_LINK_METRES = 200;
    public static final double MAX_SAME_TIME_GEOMETRY_METRES = MAX_UNTIMED_SPATIAL_LINK_METRES;

    private RecordingContinuity() {
    }

    /**
     * Preserve useful spatial geometry where time cannot support any rate or motion claim.
     */
    public static boolean hasGeometryOnlySupport(org.locationtech.jts.geom.Coordinate previous,
                                                 org.locationtech.jts.geom.Coordinate next) {
        double dt = next.getM() - previous.getM();
        if (Double.isFinite(dt) && dt != 0) return false;
        double limit = Double.isFinite(dt) ? MAX_SAME_TIME_GEOMETRY_METRES : MAX_UNTIMED_SPATIAL_LINK_METRES;
        return GPXReader.getDistanceBetweenTwoWGS84(previous, next) <= limit;
    }

    public static boolean hasTimeBreak(double previousSeconds, double nextSeconds) {
        return !Double.isFinite(previousSeconds) || !Double.isFinite(nextSeconds)
               || nextSeconds <= previousSeconds
               || nextSeconds - previousSeconds > MAX_INTERPOLATION_GAP_SECONDS;
    }

    public static GpsTrackDataPoint.IntervalStatus intervalStatus(Coordinate previous, Coordinate next) {
        if (hasGeometryOnlySupport(previous, next)) return GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY;
        return hasTimeBreak(previous.getM(), next.getM())
                ? GpsTrackDataPoint.IntervalStatus.UNKNOWN : GpsTrackDataPoint.IntervalStatus.OBSERVED;
    }

    /**
     * Stored recording boundaries take precedence; timestamps still guard unsupported interpolation.
     */
    public static boolean hasGeometrySupport(GpsTrackDataPoint previous, GpsTrackDataPoint next) {
        if (next.getIntervalStatus() == GpsTrackDataPoint.IntervalStatus.UNKNOWN
            || next.getIntervalStatus() == GpsTrackDataPoint.IntervalStatus.START) return false;
        return intervalStatus(observation(previous), observation(next)) != GpsTrackDataPoint.IntervalStatus.UNKNOWN;
    }

    /**
     * Untimed observations cannot hide a long gap between consecutive known times.
     */
    public static NavigableSet<Integer> timeBreakIndexes(List<? extends Coordinate> observations,
                                                         Collection<Integer> explicitBreaks) {
        NavigableSet<Integer> result = new TreeSet<>();
        Double known = null;
        for (int i = 0; i < observations.size(); i++) {
            if (explicitBreaks.contains(i)) known = null;
            double time = observations.get(i).getM();
            if (!Double.isFinite(time)) continue;
            if (known != null && (time < known || time - known > MAX_INTERPOLATION_GAP_SECONDS)) result.add(i);
            known = time;
        }
        return result;
    }

    public static NavigableSet<Integer> timeBreakPointIndexes(List<GpsTrackDataPoint> points) {
        return timeBreakPointIndexes(points, List.of());
    }

    public static NavigableSet<Integer> timeBreakPointIndexes(List<GpsTrackDataPoint> points,
                                                              List<GpsTrackEvent> events) {
        var observations = points.stream().map(RecordingContinuity::observation).toList();
        NavigableSet<Integer> explicitPointBreaks = new TreeSet<>();
        events.stream().filter(event -> event.getEventType() == GpsTrackEvent.EVENT_TYPE.GPS_GAP)
                .map(GpsTrackEvent::getEndPointIndex).filter(java.util.Objects::nonNull).forEach(explicitPointBreaks::add);
        NavigableSet<Integer> explicitIndexes = new TreeSet<>();
        for (int i = 0; i < points.size(); i++) {
            var point = points.get(i);
            if (point.getIntervalStatus() == GpsTrackDataPoint.IntervalStatus.START
                || point.getIntervalStatus() == GpsTrackDataPoint.IntervalStatus.UNKNOWN
                || (point.getPointIndex() != null && explicitPointBreaks.contains(point.getPointIndex()))) explicitIndexes.add(i);
        }
        NavigableSet<Integer> result = new TreeSet<>();
        for (int index : timeBreakIndexes(observations, explicitIndexes)) result.add(points.get(index).getPointIndex());
        return result;
    }

    private static Coordinate observation(GpsTrackDataPoint point) {
        return new CoordinateXYZM(point.getPointLongLat().getX(), point.getPointLongLat().getY(), Double.NaN,
                point.getPointTimestamp() == null ? Double.NaN : point.getPointTimestamp().getTime() / MILLIS_PER_SECOND);
    }
}
