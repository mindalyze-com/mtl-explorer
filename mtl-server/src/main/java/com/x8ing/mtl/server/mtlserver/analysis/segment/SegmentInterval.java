package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.gpx.RecordingContinuity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.function.Function;

/**
 * Clips by canonical position; timestamps alone cannot distinguish repeated or untimed fixes.
 */
final class SegmentInterval {
    private static final double MILLIS_PER_SECOND = 1_000.0;

    record Node(double position, Instant time, double longitude, double latitude, Double speed, Double altitude,
                Double power, Double slope, Double ascent, Double descent) {
    }

    record Piece(Node from, Node to, boolean supported, Double energyWh) {
    }

    record Slice(List<Node> nodes, List<Piece> pieces) {
    }

    private SegmentInterval() {
    }

    static Slice clip(SegmentAttemptResolver.ResolvedAttempt attempt) {
        var identity = attempt.identity();
        if (identity.startPosition() == null || identity.endPosition() == null
            || identity.endPosition() < identity.startPosition()) throw SegmentAttemptResolver.stale();
        var points = intervalPoints(attempt.points(), identity.startPosition(), identity.endPosition());
        List<Node> nodes = new ArrayList<>();
        nodes.add(boundary(points, identity.startPosition(), identity.start(), identity.startCoordinate()));
        for (var p : points) {
            if (p.getPointIndex() != null && p.getPointIndex() > identity.startPosition() && p.getPointIndex() < identity.endPosition())
                nodes.add(stored(p));
        }
        nodes.add(boundary(points, identity.endPosition(), identity.end(), identity.endCoordinate()));
        boolean timingReliable = !attempt.track().isTimingUnreliable();
        if (!timingReliable) {
            nodes.replaceAll(node -> new Node(node.position(), null, node.longitude(), node.latitude(),
                    null, node.altitude(), null, node.slope(), node.ascent(), node.descent()));
        }
        List<Piece> pieces = new ArrayList<>();
        var gapEnds = attempt.gapEnds();
        int rightIndex = 1;
        for (int i = 1; i < nodes.size(); i++) {
            Node from = nodes.get(i - 1), to = nodes.get(i);
            if (from.position() == to.position()) {
                pieces.add(new Piece(from, to, true, timingReliable ? 0d : null));
                continue;
            }
            while (rightIndex < points.size() && position(points.get(rightIndex)) < to.position()) rightIndex++;
            if (rightIndex >= points.size()) throw SegmentAttemptResolver.stale();
            var left = points.get(rightIndex - 1);
            var right = points.get(rightIndex);
            double span = position(right) - position(left);
            boolean supported = span > 0 && supported(left, right, gapEnds);
            Double energy = timingReliable && supported && energySupported(right) && nonnegative(right.getEnergyTotalWh())
                    ? right.getEnergyTotalWh() * (to.position() - from.position()) / span : null;
            pieces.add(new Piece(from, to, supported, energy));
        }
        return new Slice(List.copyOf(nodes), List.copyOf(pieces));
    }

    /**
     * Include only the endpoint brackets and interior fixes, without rescanning a full track for each lap.
     */
    private static List<GpsTrackDataPoint> intervalPoints(List<GpsTrackDataPoint> points, double start, double end) {
        int from = lowerBound(points, start);
        if (from == points.size() || position(points.get(from)) > start) from--;
        int to = lowerBound(points, end);
        if (from < 0 || to >= points.size()) throw SegmentAttemptResolver.stale();
        return points.subList(from, to + 1);
    }

    private static int lowerBound(List<GpsTrackDataPoint> points, double position) {
        int low = 0, high = points.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (position(points.get(middle)) < position) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static boolean supported(GpsTrackDataPoint from, GpsTrackDataPoint to, NavigableSet<Integer> gapEnds) {
        Integer nextGap = gapEnds.higher(from.getPointIndex());
        if (nextGap != null && nextGap <= position(to)) return false;
        return RecordingContinuity.hasGeometrySupport(from, to);
    }

    static double distance(Node from, Node to) {
        return GPXReader.getDistanceBetweenTwoWGS84(new org.locationtech.jts.geom.Coordinate(from.longitude(), from.latitude()),
                new org.locationtech.jts.geom.Coordinate(to.longitude(), to.latitude()));
    }

    static Double seconds(Instant start, Instant end) {
        return start == null || end == null ? null : (end.toEpochMilli() - start.toEpochMilli()) / MILLIS_PER_SECOND;
    }

    static boolean finite(Double number) {
        return number != null && Double.isFinite(number);
    }

    static boolean nonnegative(Double number) {
        return finite(number) && number >= 0;
    }

    private static boolean energySupported(GpsTrackDataPoint point) {
        return point.getIntervalStatus() != GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY;
    }

    private static double position(GpsTrackDataPoint point) {
        if (point.getPointIndex() == null) throw SegmentAttemptResolver.stale();
        return point.getPointIndex();
    }

    private static Node stored(GpsTrackDataPoint p) {
        return new Node(position(p), p.getPointTimestamp() == null ? null : p.getPointTimestamp().toInstant(),
                p.getPointLongLat().getX(), p.getPointLongLat().getY(), clean(p.getSpeedInKmhMovingWindow()),
                clean(p.getPointAltitude()), clean(p.getPowerWatts()), clean(p.getSlopePercentageInMovingWindow()),
                clean(p.getAscentInMeterSinceStart()), clean(p.getDescentInMeterSinceStart()));
    }

    private static Node boundary(List<GpsTrackDataPoint> points, double position, Instant time, List<Double> coordinate) {
        for (int i = 0; i < points.size(); i++) {
            var right = points.get(i);
            if (position(right) == position) return stored(right);
            if (position(right) > position && i > 0) {
                var left = points.get(i - 1);
                double fraction = (position - position(left)) / (position(right) - position(left));
                // Speed/slope are samples of the precomputed full-track window, interpolated without restarting that window.
                // Power is an interval estimate owned by the interval's ending fix.
                boolean metricSupport = energySupported(right) && right.getIntervalStatus() != GpsTrackDataPoint.IntervalStatus.UNKNOWN;
                return new Node(position, time, coordinate.get(0), coordinate.get(1),
                        metricSupport ? interpolate(left, right, fraction, GpsTrackDataPoint::getSpeedInKmhMovingWindow) : null,
                        interpolate(left, right, fraction, GpsTrackDataPoint::getPointAltitude),
                        metricSupport ? clean(right.getPowerWatts()) : null,
                        metricSupport ? interpolate(left, right, fraction, GpsTrackDataPoint::getSlopePercentageInMovingWindow) : null,
                        interpolate(left, right, fraction, GpsTrackDataPoint::getAscentInMeterSinceStart),
                        interpolate(left, right, fraction, GpsTrackDataPoint::getDescentInMeterSinceStart));
            }
        }
        throw SegmentAttemptResolver.stale();
    }

    private static Double interpolate(GpsTrackDataPoint from, GpsTrackDataPoint to, double fraction,
                                      Function<GpsTrackDataPoint, Double> metric) {
        Double a = metric.apply(from), b = metric.apply(to);
        return finite(a) && finite(b) ? a + (b - a) * fraction : null;
    }

    private static Double clean(Double value) {
        return finite(value) ? value : null;
    }
}
