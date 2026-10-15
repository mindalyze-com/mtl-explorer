package com.x8ing.mtl.server.mtlserver.logic.motion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.gpsquality.diagnostics.GpsReviewThresholds;
import com.x8ing.mtl.server.mtlserver.metrics.MetricConstants;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;

import java.util.*;

/**
 * Offline motion classification of accepted denoised observations. Never edits input geometry.
 */
@Slf4j
public final class TrackMotionAnalyzer {
    public static final String VERSION = "motion-v2";
    public static final double MIN_VISIBLE_BREAK_DURATION_S = 60;
    public static final double MAX_SUPPORT_GAP_S = 60;
    private static final double STOP_ENTER_SPEED_KMH = 0.3;
    private static final int MIN_CORE_INTERVALS = 2;
    // Recover a clearly still portion after very slow progress invalidates a larger candidate.
    private static final double QUIET_CORE_SPEED_KMH = 0.05;
    private static final double CORE_DIAMETER_M = 10;
    private static final double CORE_MAX_NET_PROGRESS_M = 5;
    private static final double BOUNDARY_RADIUS_M = 5;
    private static final double MAX_BOUNDARY_LOOKAROUND_S = 60;
    private static final double MILLIS_PER_SECOND = 1000;
    private static final double METRES_PER_DEGREE = 111_320;

    public enum State {MOVING, STOPPED, UNKNOWN}

    public enum Reason {
        START, OBSERVED_MOVEMENT, OBSERVED_STATIONARY, RECORDING_GAP, SPARSE_SUPPORT,
        INVALID_TIME, INVALID_POSITION, AMBIGUOUS_MOTION, INSUFFICIENT_OBSERVATIONS
    }

    public enum BoundaryStatus {BRACKETED, TRACK_EDGE, RECORDING_EDGE}

    public enum Status {COMPLETE, PARTIAL, UNAVAILABLE}

    @JsonPropertyOrder({"earliestTimeS", "latestTimeS", "status"})
    public record Boundary(double earliestTimeS, double latestTimeS, BoundaryStatus status) {
    }

    @JsonPropertyOrder({"state", "reason", "durationS"})
    public record Interval(State state, Reason reason, double durationS) {
    }

    @JsonPropertyOrder({"startIndex", "endIndex", "startTimeS", "endTimeS", "centerLng", "centerLat",
            "centerElevation", "startBoundary", "endBoundary", "confirmedAtS"})
    public record StationaryRange(int startIndex, int endIndex, double startTimeS, double endTimeS,
                                  double centerLng, double centerLat, double centerElevation,
                                  Boundary startBoundary, Boundary endBoundary, double confirmedAtS) {
        public double durationS() {
            return endTimeS - startTimeS;
        }
    }

    @JsonPropertyOrder({"intervals", "stationaryRanges", "status", "movingSeconds", "stoppedSeconds", "unknownSeconds", "shortStoppedSeconds"})
    public record Analysis(List<Interval> intervals, List<StationaryRange> stationaryRanges, Status status,
                           double movingSeconds, double stoppedSeconds, double unknownSeconds, double shortStoppedSeconds) {
        public List<StationaryRange> breaks() {
            return stationaryRanges.stream().filter(r -> r.durationS() >= MIN_VISIBLE_BREAK_DURATION_S).toList();
        }
    }

    private enum Evidence {CORE, MOVING, AMBIGUOUS, UNSUPPORTED}

    private record Core(int start, int end, Coordinate center) {
    }

    private TrackMotionAnalyzer() {
    }

    public static Analysis analyze(List<Coordinate> input, Collection<Integer> recordingBreaks) {
        long started = System.nanoTime();
        Set<Integer> breaks = recordingBreaks == null ? Set.of() : new HashSet<>(recordingBreaks);
        List<Coordinate> points = precisionTimeline(input == null ? List.of() : input, breaks);
        log.info("Motion analysis starting: version={} points={} recordingBreaks={}", VERSION, points.size(), breaks.size());
        int n = points.size();
        Interval[] intervals = new Interval[n];
        Evidence[] evidence = new Evidence[n];
        boolean[] quiet = new boolean[n];
        boolean invalidTimeline = false;
        int positiveIntervals = 0;
        for (int i = 0; i < n; i++) {
            evidence[i] = Evidence.UNSUPPORTED;
            intervals[i] = new Interval(State.UNKNOWN, Reason.START, 0);
            Coordinate b = points.get(i);
            if (b == null || !Double.isFinite(b.getM())) invalidTimeline = true;
            if (i == 0) continue;
            Coordinate a = points.get(i - 1);
            double dt = time(b) - time(a);
            double duration = Double.isFinite(dt) && dt > 0 ? dt : 0;
            if (duration > 0) positiveIntervals++;
            Reason unsupported = null;
            if (!Double.isFinite(dt) || dt <= 0) {
                unsupported = Reason.INVALID_TIME;
                if (!Double.isFinite(dt) || dt < 0) invalidTimeline = true;
            } else if (!validPosition(a) || !validPosition(b)) unsupported = Reason.INVALID_POSITION;
            else if (breaks.contains(i)) unsupported = Reason.RECORDING_GAP;
            else if (dt > MAX_SUPPORT_GAP_S) unsupported = Reason.SPARSE_SUPPORT;
            if (unsupported != null) {
                intervals[i] = new Interval(State.UNKNOWN, unsupported, duration);
                continue;
            }
            double horizontal = GPXReader.getDistanceBetweenTwoWGS84(a, b);
            double vertical = Double.isFinite(a.getZ()) && Double.isFinite(b.getZ()) ? Math.abs(b.getZ() - a.getZ()) : 0;
            double speed = Math.max(horizontal, vertical) / dt * MetricConstants.MPS_TO_KMH;
            quiet[i] = speed <= QUIET_CORE_SPEED_KMH;
            evidence[i] = speed >= MetricConstants.MOVING_SPEED_THRESHOLD_KMH ? Evidence.MOVING
                    : speed <= STOP_ENTER_SPEED_KMH ? Evidence.CORE : Evidence.AMBIGUOUS;
            intervals[i] = evidence[i] == Evidence.MOVING
                    ? new Interval(State.MOVING, Reason.OBSERVED_MOVEMENT, duration)
                    : new Interval(State.UNKNOWN, Reason.AMBIGUOUS_MOTION, duration);
        }

        List<Core> cores = new ArrayList<>();
        for (int i = 1; i < n; ) {
            if (evidence[i] != Evidence.CORE) {
                i++;
                continue;
            }
            int first = i;
            while (i < n && evidence[i] == Evidence.CORE) i++;
            int last = i - 1;
            if (last - first + 1 < MIN_CORE_INTERVALS) {
                intervals[first] = new Interval(State.UNKNOWN, Reason.INSUFFICIENT_OBSERVATIONS, intervals[first].durationS());
            } else if (!addCore(points, first, last, cores)) {
                // Slow progress must not erase a later still core, or be sliced into false pauses.
                for (int j = first; j <= last; ) {
                    if (!quiet[j]) {
                        j++;
                        continue;
                    }
                    int from = j;
                    while (j <= last && quiet[j]) j++;
                    if (j - from >= MIN_CORE_INTERVALS) addCore(points, from, j - 1, cores);
                }
            }
        }
        List<StationaryRange> ranges = new ArrayList<>();
        int refinedBoundaries = 0;
        for (Core core : cores) {
            int start = core.start(), end = core.end(), first = start + 1;
            Coordinate center = core.center();
            // Confirmation uses later observations; the following pass revisits the original boundaries.
            double confirmedAt = time(points.get(Math.min(end, first + MIN_CORE_INTERVALS - 1)));
            int refinedStart = refineStart(points, evidence, intervals, start, center);
            int refinedEnd = refineEnd(points, evidence, intervals, end, center);
            if (refinedStart != start) refinedBoundaries++;
            if (refinedEnd != end) refinedBoundaries++;
            for (int j = refinedStart + 1; j <= refinedEnd; j++) {
                intervals[j] = new Interval(State.STOPPED, Reason.OBSERVED_STATIONARY, intervals[j].durationS());
            }
            double startTime = time(points.get(refinedStart)), endTime = time(points.get(refinedEnd));
            if (endTime - startTime >= MIN_VISIBLE_BREAK_DURATION_S) {
                int confirmation = first + MIN_CORE_INTERVALS - 1;
                while (confirmation < refinedEnd && time(points.get(confirmation)) - startTime < MIN_VISIBLE_BREAK_DURATION_S) confirmation++;
                confirmedAt = Math.max(confirmedAt, time(points.get(confirmation)));
            }
            ranges.add(new StationaryRange(refinedStart, refinedEnd, startTime, endTime,
                    center.getX(), center.getY(), center.getZ(), boundary(points, evidence, refinedStart, start, true),
                    boundary(points, evidence, refinedEnd, end, false), confirmedAt));
        }

        // Only touching confirmed stationary spans consolidate; no movement or unknown bridge is filled.
        ranges = consolidate(ranges);
        double moving = 0, stopped = 0, unknown = 0;
        for (Interval interval : intervals) {
            switch (interval.state()) {
                case MOVING -> moving += interval.durationS();
                case STOPPED -> stopped += interval.durationS();
                case UNKNOWN -> unknown += interval.durationS();
            }
        }
        Status status = positiveIntervals == 0 ? Status.UNAVAILABLE : invalidTimeline ? Status.PARTIAL : Status.COMPLETE;
        double shortStops = ranges.stream().filter(r -> r.durationS() < MIN_VISIBLE_BREAK_DURATION_S).mapToDouble(StationaryRange::durationS).sum();
        log.info("Motion analysis completed: version={} status={} intervals={} stops={} movingSeconds={} stoppedSeconds={} unknownSeconds={} refinedBoundaries={} durationMs={}",
                VERSION, status, n, ranges.size(), moving, stopped, unknown, refinedBoundaries, (System.nanoTime() - started) / 1_000_000);
        return new Analysis(List.of(intervals), List.copyOf(ranges), status, moving, stopped, unknown, shortStops);
    }

    private static List<Coordinate> precisionTimeline(List<Coordinate> original, Set<Integer> breaks) {
        List<Coordinate> result = new ArrayList<>(original.size());
        double latest = Double.NaN;
        for (int i = 0; i < original.size(); i++) {
            Coordinate point = original.get(i);
            double timestamp = time(point);
            if (breaks.contains(i) && (!Double.isFinite(timestamp) || !Double.isFinite(latest)
                    || timestamp >= latest || latest - timestamp > GpsReviewThresholds.BACKWARD_SECONDS)) latest = Double.NaN;
            if (Double.isFinite(timestamp)) {
                if (Double.isFinite(latest) && timestamp < latest && latest - timestamp <= GpsReviewThresholds.BACKWARD_SECONDS)
                    point = new CoordinateXYZM(point.getX(), point.getY(), point.getZ(), latest);
                latest = Double.isFinite(latest) ? Math.max(latest, timestamp) : timestamp;
            }
            result.add(point);
        }
        return result;
    }

    private static boolean addCore(List<Coordinate> points, int first, int last, List<Core> cores) {
        Coordinate center = timeWeightedCenter(points, first - 1, last);
        if (!compactCore(points, first - 1, last, center)) return false;
        cores.add(new Core(first - 1, last, center));
        return true;
    }

    private static int refineStart(List<Coordinate> points, Evidence[] evidence, Interval[] intervals, int start, Coordinate center) {
        int index = start;
        while (index > 0 && evidence[index] == Evidence.AMBIGUOUS && intervals[index].state() != State.STOPPED
               && time(points.get(start)) - time(points.get(index - 1)) <= MAX_BOUNDARY_LOOKAROUND_S
               && near(points.get(index - 1), center)) index--;
        return index;
    }

    private static int refineEnd(List<Coordinate> points, Evidence[] evidence, Interval[] intervals, int end, Coordinate center) {
        int index = end;
        while (index + 1 < points.size() && evidence[index + 1] == Evidence.AMBIGUOUS && intervals[index + 1].state() != State.STOPPED
               && time(points.get(index + 1)) - time(points.get(end)) <= MAX_BOUNDARY_LOOKAROUND_S
               && near(points.get(index + 1), center)) index++;
        return index;
    }

    private static boolean near(Coordinate point, Coordinate center) {
        return GPXReader.getDistanceBetweenTwoWGS84(point, center) <= BOUNDARY_RADIUS_M
               && (!Double.isFinite(point.getZ()) || !Double.isFinite(center.getZ()) || Math.abs(point.getZ() - center.getZ()) <= BOUNDARY_RADIUS_M);
    }

    private static boolean compactCore(List<Coordinate> points, int start, int end, Coordinate center) {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int i = start; i <= end; i++) {
            Coordinate point = points.get(i);
            double x = longitudeDelta(point.getX() - center.getX()) * METRES_PER_DEGREE * Math.cos(Math.toRadians(center.getY()));
            double y = (point.getY() - center.getY()) * METRES_PER_DEGREE;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            if (!near(point, center)) return false;
        }
        return Math.hypot(maxX - minX, maxY - minY) <= CORE_DIAMETER_M
               && GPXReader.getDistanceBetweenTwoWGS84(points.get(start), points.get(end)) < CORE_MAX_NET_PROGRESS_M;
    }

    private static Coordinate timeWeightedCenter(List<Coordinate> points, int start, int end) {
        Coordinate origin = points.get(start);
        double x = 0, y = 0, z = 0, weight = 0, zWeight = 0;
        for (int i = start; i <= end; i++) {
            double w = (i > start ? time(points.get(i)) - time(points.get(i - 1)) : 0)
                       + (i < end ? time(points.get(i + 1)) - time(points.get(i)) : 0);
            Coordinate point = points.get(i);
            x += longitudeDelta(point.getX() - origin.getX()) * w;
            y += point.getY() * w;
            weight += w;
            if (Double.isFinite(point.getZ())) {
                z += point.getZ() * w;
                zWeight += w;
            }
        }
        return new CoordinateXYZM(longitudeDelta(origin.getX() + x / weight), y / weight,
                zWeight > 0 ? z / zWeight : Double.NaN, Double.NaN);
    }

    private static Boundary boundary(List<Coordinate> points, Evidence[] evidence, int index, int coreIndex, boolean start) {
        double t = time(points.get(index));
        double coreTime = time(points.get(coreIndex));
        int neighbor = start ? index - 1 : index + 1;
        if (neighbor < 0 || neighbor >= points.size()) return new Boundary(Math.min(t, coreTime), Math.max(t, coreTime), BoundaryStatus.TRACK_EDGE);
        if (evidence[start ? index : index + 1] == Evidence.UNSUPPORTED)
            return new Boundary(Math.min(t, coreTime), Math.max(t, coreTime), BoundaryStatus.RECORDING_EDGE);
        return new Boundary(start ? time(points.get(neighbor)) : coreTime, start ? coreTime : time(points.get(neighbor)), BoundaryStatus.BRACKETED);
    }

    private static List<StationaryRange> consolidate(List<StationaryRange> input) {
        List<StationaryRange> result = new ArrayList<>();
        for (StationaryRange next : input) {
            if (!result.isEmpty() && result.getLast().endIndex() == next.startIndex()) {
                StationaryRange first = result.removeLast();
                result.add(new StationaryRange(first.startIndex(), next.endIndex(), first.startTimeS(), next.endTimeS(),
                        first.centerLng(), first.centerLat(), first.centerElevation(), first.startBoundary(), next.endBoundary(),
                        Math.max(first.confirmedAtS(), next.confirmedAtS())));
            } else result.add(next);
        }
        return result;
    }

    private static double time(Coordinate point) {
        return point == null || !Double.isFinite(point.getM()) ? Double.NaN : Math.round(point.getM() * MILLIS_PER_SECOND) / MILLIS_PER_SECOND;
    }

    private static boolean validPosition(Coordinate point) {
        return point != null && Double.isFinite(point.getX()) && Double.isFinite(point.getY())
               && Math.abs(point.getX()) <= 180 && Math.abs(point.getY()) <= 90;
    }

    private static double longitudeDelta(double value) {
        return ((value + 180) % 360 + 360) % 360 - 180;
    }
}
