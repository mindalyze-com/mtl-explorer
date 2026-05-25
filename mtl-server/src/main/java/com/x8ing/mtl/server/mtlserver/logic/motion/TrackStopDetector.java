package com.x8ing.mtl.server.mtlserver.logic.motion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentNotes;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

import java.util.*;

/**
 * GPS stop detector based on dense, time-sufficient coordinate clusters.
 *
 * <p>The detector deliberately does not use speed as its primary signal. Indoor
 * GPS drift often creates large low-speed scribbles while the user is still
 * stationary. A stop is accepted when enough samples over enough time form a
 * compact cloud by percentile radius and inlier ratio, with hysteresis for
 * transient excursions and a merge pass for nearby fragments.
 */
public final class TrackStopDetector {

    public static final double MIN_STOP_DURATION_S = 30.0;

    private static final double LINEARITY_RATIO = 0.75;
    private static final double LINEARITY_MIN_NET_DISPLACEMENT_M = 10.0;
    private static final double ELEVATION_MOVEMENT_THRESHOLD_M = 10.0;
    private static final int ELEVATION_TREND_MIN_SAMPLES = 6;
    private static final int PERSISTENT_DIRECTION_MIN_VECTOR_COUNT = 4;
    private static final double PERSISTENT_DIRECTION_MIN_PATH_M = 10.0;
    private static final double PERSISTENT_DIRECTION_AUTOCORRELATION_THRESHOLD = 0.45;
    private static final int DIRECTIONAL_ENTROPY_BIN_COUNT = 8;
    private static final int DIRECTIONAL_ENTROPY_MIN_VECTOR_COUNT = 5;
    private static final double DIRECTIONAL_ENTROPY_MIN_VECTOR_LENGTH_M = 2.0;
    private static final double DIRECTIONAL_ENTROPY_MIN_PATH_M = 100.0;
    private static final double DIRECTIONAL_ENTROPY_MAX_NORMALIZED = 0.60;
    private static final double DIRECTIONAL_ENTROPY_MIN_TOP_TWO_RATIO = 0.80;
    private static final double LOCAL_LINEAR_MIN_WINDOW_DURATION_S = 20.0;
    private static final double LOCAL_LINEAR_MAX_WINDOW_DURATION_S = 45.0;
    private static final int LOCAL_LINEAR_MIN_POINTS = 4;
    private static final double LOCAL_LINEAR_MIN_NET_DISPLACEMENT_M = 5.0;
    private static final double LOCAL_LINEAR_MIN_RATIO = 0.80;
    private static final double APPROX_METERS_PER_DEGREE_LATITUDE = 111_320.0;
    private static final double MERGE_MAX_TIME_GAP_S = 300.0;
    private static final double MERGE_MAX_CENTER_DISTANCE_M = 150.0;
    private static final double EPISODE_BRIDGE_MOVEMENT_MIN_DURATION_S = 20.0;
    private static final int EPISODE_BRIDGE_MOVEMENT_MIN_POINTS = 4;
    private static final double EPISODE_BRIDGE_MOVEMENT_MIN_NET_M = 30.0;
    private static final double EPISODE_BRIDGE_MOVEMENT_MIN_PATH_M = 40.0;
    private static final double EPISODE_BRIDGE_TRANSITION_MIN_CENTER_DISTANCE_M = 30.0;
    private static final double EPISODE_BRIDGE_TRANSITION_CORE_RADIUS_M = 20.0;
    private static final int EPISODE_BRIDGE_TRANSITION_MIN_POINTS = 2;
    private static final double ANCHOR_SAME_LOCATION_RADIUS_M = 1.0;
    private static final double ANCHOR_TIME_MATCH_TOLERANCE_S = 1.1;
    private static final double PERCENTILE_80 = 80.0;
    private static final double PERCENTILE_90 = 90.0;
    private static final double SEED_ENDPOINT_EXIT_RADIUS_MULTIPLIER = 2.0;
    private static final double RECORDING_GAP_MIN_DURATION_S = 600.0;
    private static final double RECORDING_GAP_MAX_DISTANCE_M = 150.0;
    private static final double RECORDING_GAP_MAX_IMPLIED_SPEED_KMH = 0.5;
    private static final double SECONDS_PER_HOUR = 3600.0;
    private static final double METERS_PER_KILOMETER = 1000.0;

    static final List<StopProfile> PROFILES = List.of(
            new StopProfile(
                    StopCategory.MICRO_STOP,
                    30.0,
                    60.0,
                    6,
                    12.0,
                    15.0,
                    0.70,
                    45.0,
                    45.0,
                    0.80,
                    70.0,
                    15.0,
                    3),
            new StopProfile(
                    StopCategory.SHORT_STOP,
                    60.0,
                    150.0,
                    6,
                    25.0,
                    45.0,
                    0.65,
                    75.0,
                    75.0,
                    0.70,
                    100.0,
                    45.0,
                    3),
            new StopProfile(
                    StopCategory.BREAK,
                    180.0,
                    360.0,
                    12,
                    45.0,
                    240.0,
                    0.45,
                    100.0,
                    100.0,
                    0.65,
                    180.0,
                    120.0,
                    5),
            new StopProfile(
                    StopCategory.LONG_BREAK,
                    600.0,
                    7200.0,
                    8,
                    900.0,
                    1800.0,
                    0.45,
                    140.0,
                    130.0,
                    0.60,
                    280.0,
                    300.0,
                    4)
    );

    private TrackStopDetector() {
    }

    public enum StopCategory {
        MICRO_STOP,
        SHORT_STOP,
        BREAK,
        LONG_BREAK
    }

    public enum StopEvidence {
        DENSE_CLUSTER,
        RECORDING_GAP
    }

    public interface StopDuration {
        double durationInSec();
    }

    @JsonPropertyOrder({
            "category",
            "minDurationS",
            "windowS",
            "minPoints",
            "maxMedianGapS",
            "maxGapS",
            "minTimeCoverage",
            "clusterRadiusM",
            "r80ThresholdM",
            "minInlierRatio",
            "exitRadiusM",
            "exitConfirmationS",
            "minExitPoints"
    })
    public record StopProfile(StopCategory category,
                              double minDurationS,
                              double windowS,
                              int minPoints,
                              double maxMedianGapS,
                              double maxGapS,
                              double minTimeCoverage,
                              double clusterRadiusM,
                              double r80ThresholdM,
                              double minInlierRatio,
                              double exitRadiusM,
                              double exitConfirmationS,
                              int minExitPoints) {
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
            "evidence"
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
                            StopEvidence evidence) implements StopDuration {
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

    @JsonPropertyOrder({
            "coordinate",
            "timeS",
            "originalIndex"
    })
    private record TimedPoint(Coordinate coordinate,
                              double timeS,
                              int originalIndex) {
    }

    @JsonPropertyOrder({
            "startOrdinal",
            "endOrdinal",
            "profile",
            "range"
    })
    private record Candidate(int startOrdinal,
                             int endOrdinal,
                             StopProfile profile,
                             StopRange range) {
    }

    private enum EscapeStatus {
        RETURNED,
        SUSTAINED_ESCAPE,
        UNKNOWN_GAP,
        TRACK_END_UNCONFIRMED
    }

    private enum MovementVetoMode {
        CANDIDATE,
        CONSOLIDATED_EPISODE
    }

    @JsonPropertyOrder({
            "status",
            "returnOrdinal"
    })
    private record EscapePeek(EscapeStatus status, int returnOrdinal) {
    }

    public static List<StopRange> detectStopRangesInCoordinates(List<Coordinate> coordinates) {
        return detectStopRanges(toTimedPointsFromCoordinates(coordinates), true);
    }

    public static List<StopRange> detectRawStopRangesInCoordinates(List<Coordinate> coordinates) {
        return detectStopRanges(toTimedPointsFromCoordinates(coordinates), false);
    }

    static List<PointStopRange> detectStopRangesInTrack(List<GpsTrackDataPoint> points) {
        List<TimedPoint> timedPoints = toTimedPointsFromTrack(points, null, null);
        List<PointStopRange> denseStops = toPointStopRanges(points, detectStopRanges(timedPoints, true));
        return mergePointStopRanges(denseStops, detectNearbyRecordingGapBreaks(points, denseStops));
    }

    public static List<PointStopRange> detectNearbyRecordingGapBreaks(List<GpsTrackDataPoint> points,
                                                                      Collection<PointStopRange> existingStopRanges) {
        if (points == null || points.size() < 2) {
            return List.of();
        }
        List<PointStopRange> gapBreaks = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            GpsTrackDataPoint previous = points.get(i - 1);
            GpsTrackDataPoint current = points.get(i);
            RecordingGapCandidate candidate = recordingGapCandidate(previous, current);
            if (candidate == null || overlapsExistingStopRange(previous, current, existingStopRanges)) {
                continue;
            }
            gapBreaks.add(new PointStopRange(
                    previous,
                    current,
                    recordingGapStopRange(i - 1, i, previous, current, candidate.distanceM())));
        }
        return gapBreaks;
    }

    public static List<PointStopRange> mergePointStopRanges(Collection<PointStopRange> first,
                                                            Collection<PointStopRange> second) {
        List<PointStopRange> merged = new ArrayList<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        merged.sort(Comparator
                .comparingDouble((PointStopRange range) -> range.stopRange().startTimeS())
                .thenComparingInt(range -> range.startPoint().getPointIndex() != null
                        ? range.startPoint().getPointIndex()
                        : Integer.MAX_VALUE));
        return merged;
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

    public static List<PointStopRange> mapStopRangesToTrackPoints(List<GpsTrackDataPoint> points,
                                                                  List<StopRange> stopRanges) {
        if (points == null || points.isEmpty() || stopRanges == null || stopRanges.isEmpty()) {
            return List.of();
        }
        List<PointStopRange> mapped = new ArrayList<>();
        for (StopRange stopRange : stopRanges) {
            GpsTrackDataPoint start = findAnchorPoint(points, stopRange.startTimeS(), stopRange.centerLng(), stopRange.centerLat());
            GpsTrackDataPoint end = findAnchorPoint(points, stopRange.endTimeS(), stopRange.centerLng(), stopRange.centerLat());
            if (start != null && end != null && start != end) {
                mapped.add(new PointStopRange(start, end, stopRange));
            }
        }
        return mapped;
    }

    public static boolean isStopAnchorPair(GpsTrackDataPoint a, GpsTrackDataPoint b) {
        if (a == null || b == null || a.getPointTimestamp() == null || b.getPointTimestamp() == null
            || a.getPointLongLat() == null || b.getPointLongLat() == null) {
            return false;
        }
        double durationS = (b.getPointTimestamp().getTime() - a.getPointTimestamp().getTime()) / 1000.0;
        if (durationS < MIN_STOP_DURATION_S) {
            return false;
        }
        return GPXReader.getDistanceBetweenTwoWGS84(
                a.getPointLongLat().getCoordinate(),
                b.getPointLongLat().getCoordinate()) <= ANCHOR_SAME_LOCATION_RADIUS_M;
    }

    public static boolean isStopAnchorPair(Coordinate a, Coordinate b) {
        if (a == null || b == null || Double.isNaN(a.getM()) || Double.isNaN(b.getM())) {
            return false;
        }
        double durationS = b.getM() - a.getM();
        if (durationS < MIN_STOP_DURATION_S) {
            return false;
        }
        return GPXReader.getDistanceBetweenTwoWGS84(a, b) <= ANCHOR_SAME_LOCATION_RADIUS_M;
    }

    private static List<StopRange> detectStopRanges(List<TimedPoint> points, boolean includeCollapsedAnchors) {
        if (points.size() < 2) {
            return List.of();
        }

        List<StopRange> ranges = new ArrayList<>();
        int i = 0;
        while (i < points.size()) {
            if (includeCollapsedAnchors) {
                Candidate anchor = detectCollapsedAnchor(points, i);
                if (anchor != null) {
                    ranges.add(anchor.range());
                    i = anchor.endOrdinal() + 1;
                    continue;
                }
            }

            Candidate candidate = findBestStopFrom(points, i);
            if (candidate == null) {
                i++;
                continue;
            }

            ranges.add(candidate.range());
            i = candidate.endOrdinal() + 1;
        }
        return dropMicroStops(mergeNearbyStops(ranges, points));
    }

    private static List<StopRange> dropMicroStops(List<StopRange> ranges) {
        if (ranges.isEmpty()) {
            return ranges;
        }

        List<StopRange> filtered = new ArrayList<>(ranges.size());
        for (StopRange range : ranges) {
            if (range.category() != StopCategory.MICRO_STOP) {
                filtered.add(range);
            }
        }
        return filtered;
    }

    private static Candidate detectCollapsedAnchor(List<TimedPoint> points, int startOrdinal) {
        if (startOrdinal + 1 >= points.size()) {
            return null;
        }

        TimedPoint first = points.get(startOrdinal);
        TimedPoint second = points.get(startOrdinal + 1);
        boolean previousSame = startOrdinal > 0
                               && sameLocation(points.get(startOrdinal - 1).coordinate(), first.coordinate());
        boolean followingSame = startOrdinal + 2 < points.size()
                                && sameLocation(second.coordinate(), points.get(startOrdinal + 2).coordinate());
        if (previousSame || followingSame || !sameLocation(first.coordinate(), second.coordinate())) {
            return null;
        }

        double durationS = second.timeS() - first.timeS();
        if (durationS < MIN_STOP_DURATION_S) {
            return null;
        }

        StopCategory category = categoryForDuration(durationS);
        StopProfile profile = profileForCategory(category);
        StopRange range = buildRange(points, startOrdinal, startOrdinal + 1, profile, category);
        return new Candidate(startOrdinal, startOrdinal + 1, profile, range);
    }

    private static Candidate findBestStopFrom(List<TimedPoint> points, int startOrdinal) {
        Candidate best = null;
        for (StopProfile profile : PROFILES) {
            Candidate candidate = findStopForProfile(points, startOrdinal, profile);
            if (candidate == null) {
                continue;
            }
            if (best == null
                || candidate.endOrdinal() > best.endOrdinal()
                || (candidate.endOrdinal() == best.endOrdinal()
                    && candidate.profile().minDurationS() > best.profile().minDurationS())) {
                best = candidate;
            }
        }
        return best;
    }

    private static Candidate findStopForProfile(List<TimedPoint> points, int startOrdinal, StopProfile profile) {
        Candidate seed = findSeed(points, startOrdinal, profile);
        if (seed == null) {
            return null;
        }
        return extendSeed(points, seed);
    }

    private static Candidate findSeed(List<TimedPoint> points, int startOrdinal, StopProfile profile) {
        for (int endOrdinal = startOrdinal + 1; endOrdinal < points.size(); endOrdinal++) {
            if (!gapOk(points.get(endOrdinal - 1), points.get(endOrdinal), profile)) {
                break;
            }

            double durationS = points.get(endOrdinal).timeS() - points.get(startOrdinal).timeS();
            if (durationS > profile.windowS()) {
                break;
            }
            if (durationS < profile.minDurationS()
                || endOrdinal - startOrdinal + 1 < profile.minPoints()) {
                continue;
            }
            if (GPXReader.getDistanceBetweenTwoWGS84(
                    points.get(startOrdinal).coordinate(),
                    points.get(endOrdinal).coordinate()) > profile.exitRadiusM() * SEED_ENDPOINT_EXIT_RADIUS_MULTIPLIER) {
                break;
            }

            if (hasCandidateMovementVeto(points, startOrdinal, endOrdinal, profile)) {
                return null;
            }

            Candidate candidate = evaluateRange(points, startOrdinal, endOrdinal, profile);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static Candidate extendSeed(List<TimedPoint> points, Candidate seed) {
        Candidate current = seed;
        StopProfile profile = seed.profile();
        int startOrdinal = seed.startOrdinal();
        int endOrdinal = seed.endOrdinal();

        while (endOrdinal + 1 < points.size()) {
            TimedPoint endPoint = points.get(endOrdinal);
            TimedPoint next = points.get(endOrdinal + 1);
            if (!gapOk(endPoint, next, profile)) {
                break;
            }

            Coordinate center = new Coordinate(current.range().centerLng(), current.range().centerLat());
            double distanceToCenter = GPXReader.getDistanceBetweenTwoWGS84(center, next.coordinate());
            if (distanceToCenter > profile.exitRadiusM()) {
                EscapePeek peek = peekReturnAfterEscape(points, endOrdinal + 1, center, profile);
                if (peek.status() == EscapeStatus.RETURNED) {
                    Candidate absorbed = evaluateRange(points, startOrdinal, peek.returnOrdinal(), profile);
                    if (absorbed != null) {
                        current = absorbed;
                        endOrdinal = absorbed.endOrdinal();
                        continue;
                    }
                }
                break;
            }

            Candidate extended = evaluateRange(points, startOrdinal, endOrdinal + 1, profile);
            if (extended != null) {
                current = extended;
                endOrdinal = extended.endOrdinal();
                continue;
            }

            EscapePeek peek = peekReturnAfterEscape(points, endOrdinal + 1, center, profile);
            if (peek.status() == EscapeStatus.RETURNED) {
                Candidate absorbed = evaluateRange(points, startOrdinal, peek.returnOrdinal(), profile);
                if (absorbed != null) {
                    current = absorbed;
                    endOrdinal = absorbed.endOrdinal();
                    continue;
                }
            }
            break;
        }
        return current;
    }

    private static Candidate evaluateRange(List<TimedPoint> points,
                                           int startOrdinal,
                                           int endOrdinal,
                                           StopProfile profile) {
        return evaluateRange(points, startOrdinal, endOrdinal, profile, MovementVetoMode.CANDIDATE);
    }

    private static Candidate evaluateRange(List<TimedPoint> points,
                                           int startOrdinal,
                                           int endOrdinal,
                                           StopProfile profile,
                                           MovementVetoMode movementVetoMode) {
        int count = endOrdinal - startOrdinal + 1;
        if (count < profile.minPoints()) {
            return null;
        }

        double durationS = points.get(endOrdinal).timeS() - points.get(startOrdinal).timeS();
        if (durationS < profile.minDurationS()) {
            return null;
        }

        double[] gaps = gaps(points, startOrdinal, endOrdinal);
        if (gaps.length == 0 || median(gaps) > profile.maxMedianGapS() || max(gaps) > profile.maxGapS()) {
            return null;
        }
        if (timeCoverage(gaps, durationS, profile) < profile.minTimeCoverage()) {
            return null;
        }

        StopRange range = buildRange(points, startOrdinal, endOrdinal, profile, profile.category());
        if (range.radiusR80M() > profile.r80ThresholdM()
            || range.inlierRatio() < profile.minInlierRatio()
            || endsInUnconfirmedEscape(points, startOrdinal, endOrdinal, profile, range)
            || hasMovementVeto(points, startOrdinal, endOrdinal, profile, movementVetoMode)) {
            return null;
        }
        return new Candidate(startOrdinal, endOrdinal, profile, range);
    }

    private static boolean hasMovementVeto(List<TimedPoint> points,
                                           int startOrdinal,
                                           int endOrdinal,
                                           StopProfile profile,
                                           MovementVetoMode movementVetoMode) {
        return switch (movementVetoMode) {
            case CANDIDATE -> hasCandidateMovementVeto(points, startOrdinal, endOrdinal, profile);
            case CONSOLIDATED_EPISODE -> hasEpisodeMovementVeto(points, startOrdinal, endOrdinal, profile);
        };
    }

    private static boolean hasCandidateMovementVeto(List<TimedPoint> points,
                                                    int startOrdinal,
                                                    int endOrdinal,
                                                    StopProfile profile) {
        return hasPersistentDisplacementDirection(points, startOrdinal, endOrdinal)
               || hasConcentratedHeadingDistribution(points, startOrdinal, endOrdinal)
               || hasLocalLinearSubpath(points, startOrdinal, endOrdinal)
               || isLikelyLinearMovement(points, startOrdinal, endOrdinal, profile)
               || hasSignificantElevationChange(points, startOrdinal, endOrdinal);
    }

    private static boolean hasEpisodeMovementVeto(List<TimedPoint> points,
                                                  int startOrdinal,
                                                  int endOrdinal,
                                                  StopProfile profile) {
        return isLikelyLinearMovement(points, startOrdinal, endOrdinal, profile)
               || hasSignificantElevationChange(points, startOrdinal, endOrdinal);
    }

    private static StopRange buildRange(List<TimedPoint> points,
                                        int startOrdinal,
                                        int endOrdinal,
                                        StopProfile profile,
                                        StopCategory category) {
        int count = endOrdinal - startOrdinal + 1;
        double[] lngs = new double[count];
        double[] lats = new double[count];
        double[] elevations = new double[count];
        int elevationCount = 0;
        for (int i = 0; i < count; i++) {
            Coordinate coordinate = points.get(startOrdinal + i).coordinate();
            lngs[i] = coordinate.getX();
            lats[i] = coordinate.getY();
            double z = coordinate.getZ();
            if (!Double.isNaN(z)) {
                elevations[elevationCount++] = z;
            }
        }

        double centerLng = median(lngs);
        double centerLat = median(lats);
        double centerElevation = Double.NaN;
        if (elevationCount > 0) {
            centerElevation = median(Arrays.copyOf(elevations, elevationCount));
        }

        Coordinate center = new Coordinate(centerLng, centerLat);
        double[] distances = new double[count];
        int inliers = 0;
        for (int i = 0; i < count; i++) {
            double distance = GPXReader.getDistanceBetweenTwoWGS84(center, points.get(startOrdinal + i).coordinate());
            distances[i] = distance;
            if (distance <= profile.clusterRadiusM()) {
                inliers++;
            }
        }

        TimedPoint start = points.get(startOrdinal);
        TimedPoint end = points.get(endOrdinal);
        return new StopRange(
                start.originalIndex(),
                end.originalIndex(),
                start.timeS(),
                end.timeS(),
                centerLng,
                centerLat,
                centerElevation,
                category,
                percentile(distances, PERCENTILE_80),
                percentile(distances, PERCENTILE_90),
                inliers / (double) count,
                count,
                Math.max(0, count - 2));
    }

    private static boolean isLikelyLinearMovement(List<TimedPoint> points,
                                                  int startOrdinal,
                                                  int endOrdinal,
                                                  StopProfile profile) {
        double pathLength = pathLength(points, startOrdinal, endOrdinal);
        if (pathLength <= 0.0) {
            return false;
        }

        double netDisplacement = GPXReader.getDistanceBetweenTwoWGS84(
                points.get(startOrdinal).coordinate(),
                points.get(endOrdinal).coordinate());
        return pathLength >= LINEARITY_MIN_NET_DISPLACEMENT_M
               && netDisplacement / pathLength >= LINEARITY_RATIO
               && netDisplacement > LINEARITY_MIN_NET_DISPLACEMENT_M;
    }

    private static double pathLength(List<TimedPoint> points, int startOrdinal, int endOrdinal) {
        double pathLength = 0.0;
        for (int i = startOrdinal + 1; i <= endOrdinal; i++) {
            pathLength += GPXReader.getDistanceBetweenTwoWGS84(
                    points.get(i - 1).coordinate(),
                    points.get(i).coordinate());
        }
        return pathLength;
    }

    private static boolean hasSignificantElevationChange(List<TimedPoint> points,
                                                         int startOrdinal,
                                                         int endOrdinal) {
        double[] elevations = new double[endOrdinal - startOrdinal + 1];
        int elevationCount = 0;
        for (int i = startOrdinal; i <= endOrdinal; i++) {
            double z = points.get(i).coordinate().getZ();
            if (!Double.isNaN(z)) {
                elevations[elevationCount++] = z;
            }
        }
        if (elevationCount < ELEVATION_TREND_MIN_SAMPLES) {
            return false;
        }

        int thirdSize = Math.max(1, elevationCount / 3);
        double[] firstThird = Arrays.copyOfRange(elevations, 0, thirdSize);
        double[] lastThird = Arrays.copyOfRange(elevations, elevationCount - thirdSize, elevationCount);
        return Math.abs(median(lastThird) - median(firstThird)) >= ELEVATION_MOVEMENT_THRESHOLD_M;
    }

    private static boolean hasPersistentDisplacementDirection(List<TimedPoint> points,
                                                              int startOrdinal,
                                                              int endOrdinal) {
        int vectorCount = endOrdinal - startOrdinal;
        if (vectorCount < PERSISTENT_DIRECTION_MIN_VECTOR_COUNT) {
            return false;
        }

        double[] adjacentDirectionCorrelations = new double[Math.max(0, vectorCount - 1)];
        int correlationCount = 0;
        int nonZeroVectorCount = 0;
        double pathLength = 0.0;
        Coordinate origin = points.get(startOrdinal).coordinate();
        double metersPerDegreeLongitude = metersPerDegreeLongitude(origin.getY());
        double previousX = 0.0;
        double previousY = 0.0;
        double previousUnitDx = 0.0;
        double previousUnitDy = 0.0;

        for (int i = 0; i < vectorCount; i++) {
            Coordinate coordinate = points.get(startOrdinal + i + 1).coordinate();
            double currentX = (coordinate.getX() - origin.getX()) * metersPerDegreeLongitude;
            double currentY = (coordinate.getY() - origin.getY()) * APPROX_METERS_PER_DEGREE_LATITUDE;
            double dx = currentX - previousX;
            double dy = currentY - previousY;
            double segmentLength = Math.hypot(dx, dy);
            if (segmentLength > 0.0) {
                double unitDx = dx / segmentLength;
                double unitDy = dy / segmentLength;
                if (nonZeroVectorCount > 0) {
                    adjacentDirectionCorrelations[correlationCount++] =
                            previousUnitDx * unitDx + previousUnitDy * unitDy;
                }
                previousUnitDx = unitDx;
                previousUnitDy = unitDy;
                nonZeroVectorCount++;
            }
            pathLength += segmentLength;
            previousX = currentX;
            previousY = currentY;
        }
        if (nonZeroVectorCount < PERSISTENT_DIRECTION_MIN_VECTOR_COUNT
            || pathLength < PERSISTENT_DIRECTION_MIN_PATH_M) {
            return false;
        }

        Arrays.sort(adjacentDirectionCorrelations, 0, correlationCount);
        double autocorrelation = medianSorted(adjacentDirectionCorrelations, correlationCount);
        return autocorrelation >= PERSISTENT_DIRECTION_AUTOCORRELATION_THRESHOLD;
    }

    private static boolean hasConcentratedHeadingDistribution(List<TimedPoint> points,
                                                              int startOrdinal,
                                                              int endOrdinal) {
        int[] bins = new int[DIRECTIONAL_ENTROPY_BIN_COUNT];
        int vectorCount = 0;
        double pathLengthM = 0.0;
        Coordinate origin = points.get(startOrdinal).coordinate();
        double metersPerDegreeLongitude = metersPerDegreeLongitude(origin.getY());
        double previousX = 0.0;
        double previousY = 0.0;

        for (int i = startOrdinal + 1; i <= endOrdinal; i++) {
            Coordinate coordinate = points.get(i).coordinate();
            double currentX = (coordinate.getX() - origin.getX()) * metersPerDegreeLongitude;
            double currentY = (coordinate.getY() - origin.getY()) * APPROX_METERS_PER_DEGREE_LATITUDE;
            double dx = currentX - previousX;
            double dy = currentY - previousY;
            double segmentLengthM = Math.hypot(dx, dy);
            previousX = currentX;
            previousY = currentY;

            if (segmentLengthM < DIRECTIONAL_ENTROPY_MIN_VECTOR_LENGTH_M) {
                continue;
            }

            pathLengthM += segmentLengthM;
            vectorCount++;
            bins[headingBin(dx, dy)]++;
        }

        if (vectorCount < DIRECTIONAL_ENTROPY_MIN_VECTOR_COUNT
            || pathLengthM < DIRECTIONAL_ENTROPY_MIN_PATH_M) {
            return false;
        }

        double entropy = 0.0;
        int top1 = 0;
        int top2 = 0;
        for (int count : bins) {
            if (count > top1) {
                top2 = top1;
                top1 = count;
            } else if (count > top2) {
                top2 = count;
            }

            if (count > 0) {
                double p = count / (double) vectorCount;
                entropy -= p * Math.log(p);
            }
        }

        double normalizedEntropy = entropy / Math.log(DIRECTIONAL_ENTROPY_BIN_COUNT);
        double topTwoRatio = (top1 + top2) / (double) vectorCount;
        return normalizedEntropy <= DIRECTIONAL_ENTROPY_MAX_NORMALIZED
               && topTwoRatio >= DIRECTIONAL_ENTROPY_MIN_TOP_TWO_RATIO;
    }

    private static int headingBin(double dx, double dy) {
        double degrees = Math.toDegrees(Math.atan2(dx, dy));
        if (degrees < 0.0) {
            degrees += 360.0;
        }
        double binWidth = 360.0 / DIRECTIONAL_ENTROPY_BIN_COUNT;
        return Math.min(DIRECTIONAL_ENTROPY_BIN_COUNT - 1, (int) Math.floor(degrees / binWidth));
    }

    private static boolean hasLocalLinearSubpath(List<TimedPoint> points, int startOrdinal, int endOrdinal) {
        for (int i = startOrdinal; i <= endOrdinal; i++) {
            double pathLengthM = 0.0;
            for (int j = i + 1; j <= endOrdinal; j++) {
                pathLengthM += GPXReader.getDistanceBetweenTwoWGS84(
                        points.get(j - 1).coordinate(),
                        points.get(j).coordinate());

                double durationS = points.get(j).timeS() - points.get(i).timeS();
                if (durationS > LOCAL_LINEAR_MAX_WINDOW_DURATION_S) {
                    break;
                }
                if (durationS < LOCAL_LINEAR_MIN_WINDOW_DURATION_S
                    || j - i + 1 < LOCAL_LINEAR_MIN_POINTS
                    || pathLengthM <= 0.0) {
                    continue;
                }

                double netDisplacementM = GPXReader.getDistanceBetweenTwoWGS84(
                        points.get(i).coordinate(),
                        points.get(j).coordinate());
                if (netDisplacementM >= LOCAL_LINEAR_MIN_NET_DISPLACEMENT_M
                    && netDisplacementM / pathLengthM >= LOCAL_LINEAR_MIN_RATIO) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double metersPerDegreeLongitude(double latitude) {
        return APPROX_METERS_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(latitude));
    }

    private static boolean endsInUnconfirmedEscape(List<TimedPoint> points,
                                                   int startOrdinal,
                                                   int endOrdinal,
                                                   StopProfile profile,
                                                   StopRange range) {
        if (endOrdinal <= startOrdinal) {
            return false;
        }

        Coordinate center = new Coordinate(range.centerLng(), range.centerLat());
        double finalDistance = GPXReader.getDistanceBetweenTwoWGS84(center, points.get(endOrdinal).coordinate());
        if (finalDistance <= profile.exitRadiusM()) {
            return false;
        }

        for (int i = endOrdinal; i >= startOrdinal; i--) {
            double distance = GPXReader.getDistanceBetweenTwoWGS84(center, points.get(i).coordinate());
            if (distance <= profile.clusterRadiusM()) {
                break;
            }
            return true;
        }
        return false;
    }

    private static EscapePeek peekReturnAfterEscape(List<TimedPoint> points,
                                                    int outsideOrdinal,
                                                    Coordinate center,
                                                    StopProfile profile) {
        double startT = points.get(outsideOrdinal).timeS();
        int outsideCount = 0;
        for (int i = outsideOrdinal; i < points.size(); i++) {
            if (i > outsideOrdinal && !gapOk(points.get(i - 1), points.get(i), profile)) {
                return new EscapePeek(EscapeStatus.UNKNOWN_GAP, -1);
            }

            TimedPoint point = points.get(i);
            double distance = GPXReader.getDistanceBetweenTwoWGS84(center, point.coordinate());
            if (distance <= profile.clusterRadiusM()) {
                return new EscapePeek(EscapeStatus.RETURNED, i);
            }
            if (distance > profile.exitRadiusM()) {
                outsideCount++;
            }

            double escapeDurationS = point.timeS() - startT;
            if (escapeDurationS >= profile.exitConfirmationS()
                && outsideCount >= profile.minExitPoints()) {
                return new EscapePeek(EscapeStatus.SUSTAINED_ESCAPE, -1);
            }
        }
        return new EscapePeek(EscapeStatus.TRACK_END_UNCONFIRMED, -1);
    }

    private static List<StopRange> mergeNearbyStops(List<StopRange> ranges, List<TimedPoint> points) {
        if (ranges.size() < 2) {
            return ranges;
        }

        List<StopRange> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingDouble(StopRange::startTimeS));

        List<StopRange> merged = new ArrayList<>();
        StopRange current = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            StopRange next = sorted.get(i);
            double gapS = next.startTimeS() - current.endTimeS();
            double centerDistanceM = GPXReader.getDistanceBetweenTwoWGS84(
                    new Coordinate(current.centerLng(), current.centerLat()),
                    new Coordinate(next.centerLng(), next.centerLat()));
            if (gapS <= MERGE_MAX_TIME_GAP_S && centerDistanceM <= MERGE_MAX_CENTER_DISTANCE_M) {
                StopRange rebuilt = tryRebuildMergedRange(current, next, points);
                if (rebuilt != null) {
                    current = rebuilt;
                } else {
                    merged.add(current);
                    current = next;
                }
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static StopRange tryRebuildMergedRange(StopRange first, StopRange second, List<TimedPoint> points) {
        int startOrdinal = ordinalForOriginalIndex(points, first.startIndex());
        int endOrdinal = ordinalForOriginalIndex(points, second.endIndex());
        int firstEndOrdinal = ordinalForOriginalIndex(points, first.endIndex());
        int secondStartOrdinal = ordinalForOriginalIndex(points, second.startIndex());
        if (startOrdinal < 0 || endOrdinal < 0 || endOrdinal <= startOrdinal
            || firstEndOrdinal < 0 || secondStartOrdinal < 0 || secondStartOrdinal <= firstEndOrdinal) {
            return null;
        }
        double durationS = points.get(endOrdinal).timeS() - points.get(startOrdinal).timeS();
        StopProfile profile = profileForCategory(categoryForDuration(durationS));
        if (hasConfirmedBridgeMovement(points, firstEndOrdinal, secondStartOrdinal, profile, first)
            || hasBridgeMovementEvidence(points, firstEndOrdinal, secondStartOrdinal, profile, first, second)) {
            return null;
        }

        Candidate candidate = evaluateRange(
                points,
                startOrdinal,
                endOrdinal,
                profile,
                MovementVetoMode.CONSOLIDATED_EPISODE);
        if (candidate == null) {
            return null;
        }
        return candidate.range();
    }

    private static boolean hasConfirmedBridgeMovement(List<TimedPoint> points,
                                                      int firstEndOrdinal,
                                                      int secondStartOrdinal,
                                                      StopProfile profile,
                                                      StopRange first) {
        if (secondStartOrdinal <= firstEndOrdinal + 1) {
            return false;
        }

        Coordinate center = new Coordinate(first.centerLng(), first.centerLat());
        double escapeStartTimeS = Double.NaN;
        int outsidePoints = 0;
        for (int i = firstEndOrdinal + 1; i < secondStartOrdinal; i++) {
            TimedPoint point = points.get(i);
            double distanceM = GPXReader.getDistanceBetweenTwoWGS84(center, point.coordinate());
            if (distanceM <= profile.clusterRadiusM()) {
                escapeStartTimeS = Double.NaN;
                outsidePoints = 0;
                continue;
            }
            if (distanceM > profile.exitRadiusM()) {
                if (Double.isNaN(escapeStartTimeS)) {
                    escapeStartTimeS = point.timeS();
                    outsidePoints = 0;
                }
                outsidePoints++;
                if (point.timeS() - escapeStartTimeS >= profile.exitConfirmationS()
                    && outsidePoints >= profile.minExitPoints()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasBridgeMovementEvidence(List<TimedPoint> points,
                                                     int firstEndOrdinal,
                                                     int secondStartOrdinal,
                                                     StopProfile profile,
                                                     StopRange first,
                                                     StopRange second) {
        int count = secondStartOrdinal - firstEndOrdinal + 1;
        if (count < EPISODE_BRIDGE_MOVEMENT_MIN_POINTS) {
            return false;
        }

        double durationS = points.get(secondStartOrdinal).timeS() - points.get(firstEndOrdinal).timeS();
        if (durationS < EPISODE_BRIDGE_MOVEMENT_MIN_DURATION_S) {
            return false;
        }

        double netDisplacementM = GPXReader.getDistanceBetweenTwoWGS84(
                points.get(firstEndOrdinal).coordinate(),
                points.get(secondStartOrdinal).coordinate());
        if (netDisplacementM < EPISODE_BRIDGE_MOVEMENT_MIN_NET_M) {
            return false;
        }

        double pathLengthM = pathLength(points, firstEndOrdinal, secondStartOrdinal);
        if (pathLengthM < EPISODE_BRIDGE_MOVEMENT_MIN_PATH_M) {
            return false;
        }

        return isLikelyLinearMovement(points, firstEndOrdinal, secondStartOrdinal, profile)
               || hasPersistentDisplacementDirection(points, firstEndOrdinal, secondStartOrdinal)
               || hasSignificantElevationChange(points, firstEndOrdinal, secondStartOrdinal)
               || hasOffCenterBridgeTransit(points, firstEndOrdinal, secondStartOrdinal, first, second);
    }

    private static boolean hasOffCenterBridgeTransit(List<TimedPoint> points,
                                                     int firstEndOrdinal,
                                                     int secondStartOrdinal,
                                                     StopRange first,
                                                     StopRange second) {
        Coordinate firstCenter = new Coordinate(first.centerLng(), first.centerLat());
        Coordinate secondCenter = new Coordinate(second.centerLng(), second.centerLat());
        double centerDistanceM = GPXReader.getDistanceBetweenTwoWGS84(firstCenter, secondCenter);
        if (centerDistanceM < EPISODE_BRIDGE_TRANSITION_MIN_CENTER_DISTANCE_M) {
            return false;
        }

        int offCenterPoints = 0;
        for (int i = firstEndOrdinal + 1; i < secondStartOrdinal; i++) {
            Coordinate coordinate = points.get(i).coordinate();
            double distanceToFirstM = GPXReader.getDistanceBetweenTwoWGS84(firstCenter, coordinate);
            double distanceToSecondM = GPXReader.getDistanceBetweenTwoWGS84(secondCenter, coordinate);
            if (distanceToFirstM > EPISODE_BRIDGE_TRANSITION_CORE_RADIUS_M
                && distanceToSecondM > EPISODE_BRIDGE_TRANSITION_CORE_RADIUS_M) {
                offCenterPoints++;
                if (offCenterPoints >= EPISODE_BRIDGE_TRANSITION_MIN_POINTS) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<PointStopRange> toPointStopRanges(List<GpsTrackDataPoint> points, List<StopRange> ranges) {
        if (points == null || points.isEmpty() || ranges.isEmpty()) {
            return List.of();
        }

        List<PointStopRange> result = new ArrayList<>();
        for (StopRange range : ranges) {
            if (range.startIndex() < 0 || range.startIndex() >= points.size()
                || range.endIndex() < 0 || range.endIndex() >= points.size()) {
                continue;
            }
            GpsTrackDataPoint start = points.get(range.startIndex());
            GpsTrackDataPoint end = points.get(range.endIndex());
            if (start.getPointTimestamp() != null && end.getPointTimestamp() != null) {
                result.add(new PointStopRange(start, end, range));
            }
        }
        return result;
    }

    @JsonPropertyOrder({
            "distanceM"
    })
    private record RecordingGapCandidate(double distanceM) {
    }

    private static RecordingGapCandidate recordingGapCandidate(GpsTrackDataPoint start, GpsTrackDataPoint end) {
        if (start == null || end == null
            || start.getPointTimestamp() == null || end.getPointTimestamp() == null
            || start.getPointLongLat() == null || end.getPointLongLat() == null) {
            return null;
        }
        double durationS = (end.getPointTimestamp().getTime() - start.getPointTimestamp().getTime()) / 1000.0;
        if (durationS < RECORDING_GAP_MIN_DURATION_S) {
            return null;
        }
        double distanceM = GPXReader.getDistanceBetweenTwoWGS84(
                start.getPointLongLat().getCoordinate(),
                end.getPointLongLat().getCoordinate());
        if (distanceM > RECORDING_GAP_MAX_DISTANCE_M) {
            return null;
        }
        double impliedSpeedKmh = distanceM / durationS * SECONDS_PER_HOUR / METERS_PER_KILOMETER;
        if (impliedSpeedKmh > RECORDING_GAP_MAX_IMPLIED_SPEED_KMH) {
            return null;
        }
        return new RecordingGapCandidate(distanceM);
    }

    private static boolean overlapsExistingStopRange(GpsTrackDataPoint start,
                                                     GpsTrackDataPoint end,
                                                     Collection<PointStopRange> existingStopRanges) {
        if (existingStopRanges == null || existingStopRanges.isEmpty()
            || start == null || end == null
            || start.getPointTimestamp() == null || end.getPointTimestamp() == null) {
            return false;
        }
        long startMs = start.getPointTimestamp().getTime();
        long endMs = end.getPointTimestamp().getTime();
        for (PointStopRange range : existingStopRanges) {
            if (range == null
                || range.startPoint() == null || range.endPoint() == null
                || range.startPoint().getPointTimestamp() == null
                || range.endPoint().getPointTimestamp() == null) {
                continue;
            }
            long rangeStartMs = range.startPoint().getPointTimestamp().getTime();
            long rangeEndMs = range.endPoint().getPointTimestamp().getTime();
            if (startMs < rangeEndMs && endMs > rangeStartMs) {
                return true;
            }
        }
        return false;
    }

    private static StopRange recordingGapStopRange(int startIndex,
                                                   int endIndex,
                                                   GpsTrackDataPoint start,
                                                   GpsTrackDataPoint end,
                                                   double distanceM) {
        Coordinate startCoordinate = start.getPointLongLat().getCoordinate();
        Coordinate endCoordinate = end.getPointLongLat().getCoordinate();
        double startElevation = start.getPointAltitude() != null ? start.getPointAltitude() : Double.NaN;
        double endElevation = end.getPointAltitude() != null ? end.getPointAltitude() : Double.NaN;
        double centerElevation = centerElevation(startElevation, endElevation);
        return new StopRange(
                startIndex,
                endIndex,
                start.getPointTimestamp().getTime() / 1000.0,
                end.getPointTimestamp().getTime() / 1000.0,
                (startCoordinate.getX() + endCoordinate.getX()) / 2.0,
                (startCoordinate.getY() + endCoordinate.getY()) / 2.0,
                centerElevation,
                StopCategory.LONG_BREAK,
                distanceM / 2.0,
                distanceM / 2.0,
                1.0,
                2,
                0,
                StopEvidence.RECORDING_GAP);
    }

    private static double centerElevation(double startElevation, double endElevation) {
        if (!Double.isNaN(startElevation) && !Double.isNaN(endElevation)) {
            return (startElevation + endElevation) / 2.0;
        }
        if (!Double.isNaN(startElevation)) {
            return startElevation;
        }
        return endElevation;
    }

    private static GpsTrackDataPoint findAnchorPoint(List<GpsTrackDataPoint> points,
                                                     double timeS,
                                                     double centerLng,
                                                     double centerLat) {
        GpsTrackDataPoint bestMatch = null;
        double bestTimeDiffS = Double.MAX_VALUE;
        double bestDistanceM = Double.MAX_VALUE;
        Coordinate center = new Coordinate(centerLng, centerLat);
        for (GpsTrackDataPoint point : points) {
            if (point.getPointTimestamp() == null) {
                continue;
            }
            double pointTimeS = point.getPointTimestamp().getTime() / 1000.0;
            double timeDiffS = Math.abs(pointTimeS - timeS);
            if (timeDiffS > ANCHOR_TIME_MATCH_TOLERANCE_S) {
                continue;
            }

            double distanceM = Double.MAX_VALUE;
            if (point.getPointLongLat() != null) {
                distanceM = GPXReader.getDistanceBetweenTwoWGS84(center, point.getPointLongLat().getCoordinate());
            }

            if (timeDiffS < bestTimeDiffS
                || (Double.compare(timeDiffS, bestTimeDiffS) == 0 && distanceM < bestDistanceM)) {
                bestTimeDiffS = timeDiffS;
                bestDistanceM = distanceM;
                bestMatch = point;
            }
        }
        return bestMatch;
    }

    private static List<TimedPoint> toTimedPointsFromCoordinates(List<Coordinate> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return List.of();
        }
        List<TimedPoint> points = new ArrayList<>(coordinates.size());
        for (int i = 0; i < coordinates.size(); i++) {
            Coordinate coordinate = coordinates.get(i);
            if (coordinate == null
                || Double.isNaN(coordinate.getX())
                || Double.isNaN(coordinate.getY())
                || Double.isNaN(coordinate.getM())) {
                continue;
            }
            points.add(new TimedPoint(coordinate, coordinate.getM(), i));
        }
        return points;
    }

    private static List<TimedPoint> toTimedPointsFromTrack(List<GpsTrackDataPoint> points, Long fromMs, Long toMs) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<TimedPoint> timedPoints = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            GpsTrackDataPoint point = points.get(i);
            Date timestamp = point.getPointTimestamp();
            Point longLat = point.getPointLongLat();
            if (timestamp == null || longLat == null) {
                continue;
            }
            long timeMs = timestamp.getTime();
            if (fromMs != null && timeMs < fromMs) {
                continue;
            }
            if (toMs != null && timeMs > toMs) {
                continue;
            }

            Coordinate coordinate = new Coordinate(
                    longLat.getX(),
                    longLat.getY(),
                    point.getPointAltitude() != null ? point.getPointAltitude() : Double.NaN);
            timedPoints.add(new TimedPoint(coordinate, timeMs / 1000.0, i));
        }
        return timedPoints;
    }

    private static boolean gapOk(TimedPoint a, TimedPoint b, StopProfile profile) {
        double gapS = b.timeS() - a.timeS();
        return !Double.isNaN(gapS) && gapS >= 0.0 && gapS <= profile.maxGapS();
    }

    private static double[] gaps(List<TimedPoint> points, int startOrdinal, int endOrdinal) {
        double[] gaps = new double[endOrdinal - startOrdinal];
        for (int i = startOrdinal + 1; i <= endOrdinal; i++) {
            gaps[i - startOrdinal - 1] = points.get(i).timeS() - points.get(i - 1).timeS();
        }
        return gaps;
    }

    private static double timeCoverage(double[] gaps, double durationS, StopProfile profile) {
        if (durationS <= 0.0) {
            return 0.0;
        }
        double covered = 0.0;
        for (double gap : gaps) {
            covered += Math.min(gap, profile.maxMedianGapS());
        }
        return covered / durationS;
    }

    private static boolean sameLocation(Coordinate a, Coordinate b) {
        return GPXReader.getDistanceBetweenTwoWGS84(a, b) <= ANCHOR_SAME_LOCATION_RADIUS_M;
    }

    private static int ordinalForOriginalIndex(List<TimedPoint> points, int originalIndex) {
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).originalIndex() == originalIndex) {
                return i;
            }
        }
        return -1;
    }

    private static StopCategory categoryForDuration(double durationS) {
        if (durationS >= 600.0) {
            return StopCategory.LONG_BREAK;
        }
        if (durationS >= 180.0) {
            return StopCategory.BREAK;
        }
        if (durationS >= 60.0) {
            return StopCategory.SHORT_STOP;
        }
        return StopCategory.MICRO_STOP;
    }

    private static StopProfile profileForCategory(StopCategory category) {
        for (StopProfile profile : PROFILES) {
            if (profile.category() == category) {
                return profile;
            }
        }
        return PROFILES.get(0);
    }

    private static double median(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return medianSorted(sorted, sorted.length);
    }

    private static double medianSorted(double[] sorted, int length) {
        if (length == 0) {
            return 0.0;
        }
        int n = length;
        return n % 2 == 1
                ? sorted[n / 2]
                : 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]);
    }

    private static double percentile(double[] values, double percentile) {
        if (values.length == 0) {
            return 0.0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        index = Math.max(0, Math.min(sorted.length - 1, index));
        return sorted[index];
    }

    private static double max(double[] values) {
        double max = 0.0;
        for (double value : values) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }
}
