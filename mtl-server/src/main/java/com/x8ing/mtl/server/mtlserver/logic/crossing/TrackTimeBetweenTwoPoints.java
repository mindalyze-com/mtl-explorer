package com.x8ing.mtl.server.mtlserver.logic.crossing;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentReadScope;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.gpx.RecordedPathGeometry;
import com.x8ing.mtl.server.mtlserver.gpx.RecordingContinuity;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.*;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer;
import lombok.extern.slf4j.Slf4j;
import net.sf.geographiclib.Geodesic;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@JsonPropertyOrder({"gpsTrackRepository", "gpsTrackDataPointRepository", "gpsTrackEventRepository", "gpsTrackVariantSelector"})
public class TrackTimeBetweenTwoPoints {
    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final double MPS_TO_KMH = 3.6;
    private static final double GEOMETRY_TOLERANCE_M = 0.000001;
    private static final double MIN_ZONE_EXIT_MARGIN_M = 2.0;
    private static final double ZONE_EXIT_MARGIN_FRACTION = 0.2;
    private static final double GOLDEN_RATIO_COMPLEMENT = (Math.sqrt(5.0) - 1.0) / 2.0;
    private static final int MINIMUM_SEARCH_ITERATIONS = 44;
    private static final int QUALIFICATION_BATCH_POINT_BUDGET = 20_000;
    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    private final GpsTrackRepository gpsTrackRepository;
    private final GpsTrackDataPointRepository gpsTrackDataPointRepository;
    private final GpsTrackEventRepository gpsTrackEventRepository;
    private final GpsTrackVariantSelector gpsTrackVariantSelector;
    private final SegmentReadScope readScope;

    public TrackTimeBetweenTwoPoints(GpsTrackRepository tracks, GpsTrackDataPointRepository points,
                                     GpsTrackEventRepository events, GpsTrackVariantSelector variants, SegmentReadScope readScope) {
        this.gpsTrackRepository = tracks;
        this.gpsTrackDataPointRepository = points;
        this.gpsTrackEventRepository = events;
        this.gpsTrackVariantSelector = variants;
        this.readScope = readScope;
    }

    /**
     * Geometry and timing only; visit numbers and attempt summaries are not prepared.
     */
    public CrossingPointsResponse getTrackCrossingsWithoutSummaries(CrossingPointsRequest request, Long[] filterIds) {
        return getTrackTimeBetweenPoints(request, filterIds, (track, points, events) -> {
        });
    }

    @FunctionalInterface
    public interface TrackSummaryCalculator {
        void summarize(CrossingsPerTrack track, List<GpsTrackDataPoint> points, List<GpsTrackEvent> events);
    }

    public CrossingPointsResponse getTrackTimeBetweenPoints(CrossingPointsRequest request, Long[] filterIds,
                                                            TrackSummaryCalculator summaries) {
        long started = System.currentTimeMillis();
        validate(request);
        log.info("Segment calculation starting: markers={} radius={}m", request.triggerPoints.size(), request.radius);
        try {
            Set<Long> candidates = new LinkedHashSet<>();
            Set<Long> routeCandidates = null;
            Map<String, Set<Long>> candidatesPerZone = new LinkedHashMap<>();
            Map<String, Integer> tracksPerZone = new LinkedHashMap<>();
            for (TriggerPoint marker : request.triggerPoints) {
                var ids = new LinkedHashSet<>(readScope.read(() -> gpsTrackRepository.getSegmentCandidateTrackIds(
                        marker.coordinate.x, marker.coordinate.y, request.radius, filterIds)));
                candidatesPerZone.put(marker.name, ids);
                candidates.addAll(ids);
                if (routeCandidates == null) routeCandidates = new LinkedHashSet<>(ids);
                else routeCandidates.retainAll(ids);
                tracksPerZone.put(marker.name, 0);
            }
            List<CrossingsPerTrack> results = new ArrayList<>();
            for (Long id : routeCandidates) {
                var result = readScope.read(() -> processTrack(request, id, summaries));
                if (result == null) continue;
                Set<String> visited = new HashSet<>();
                result.getCrossings().forEach(c -> visited.add(c.triggerPoint.name));
                visited.forEach(name -> tracksPerZone.compute(name, (ignored, count) -> count + 1));
                if (visited.size() == request.triggerPoints.size()) results.add(result);
            }
            // Exact per-zone counts still include tracks outside the route intersection.
            candidates.removeAll(routeCandidates);
            qualifyOtherCandidates(request, new ArrayList<>(candidates), candidatesPerZone, tracksPerZone);
            var response = new CrossingPointsResponse();
            Map<MarkerPair, Segment> segments = new LinkedHashMap<>();
            int unavailable = 0;
            for (var result : results) {
                response.crossings.put(result.gpsTrack.getId(), result);
                var crossings = result.getCrossings();
                for (int i = 1; i < crossings.size(); i++) {
                    Crossing current = crossings.get(i);
                    if (current.timingStatusSinceLastTriggerPoint != SegmentTimingStatus.AVAILABLE
                        || current.recordingGapSinceLastTriggerPoint) {
                        unavailable++;
                        continue;
                    }
                    var key = new MarkerPair(crossings.get(i - 1).triggerPoint.name, current.triggerPoint.name);
                    var segment = segments.computeIfAbsent(key,
                            pair -> new Segment(pair.from(), pair.to(), pair.from() + " - " + pair.to(), 0, 0d));
                    segment.count++;
                    segment.timeDelta += current.timeInSecSinceLastTriggerPoint;
                }
            }
            response.segmentsStats = segments.values().stream().sorted(Comparator.comparingInt((Segment s) -> s.count).reversed()
                    .thenComparing(s -> s.point1).thenComparing(s -> s.point2)).toList();
            response.triggerPoints = request.triggerPoints;
            response.tracksPerZone = tracksPerZone;
            log.info("Segment calculation completed: candidateTracks={} crossings={} unavailableTraversals={} durationMs={}",
                    routeCandidates.size() + candidates.size(), results.stream().mapToInt(r -> r.getCrossings().size()).sum(), unavailable,
                    System.currentTimeMillis() - started);
            return response;
        } catch (RuntimeException exception) {
            log.error("Segment calculation failed: markers={} type={} durationMs={}", request.triggerPoints.size(),
                    exception.getClass().getSimpleName(), System.currentTimeMillis() - started, exception);
            throw exception;
        }
    }

    private void qualifyOtherCandidates(CrossingPointsRequest request, List<Long> trackIds,
                                        Map<String, Set<Long>> candidatesPerZone, Map<String, Integer> tracksPerZone) {
        if (trackIds.isEmpty()) return;
        long started = System.currentTimeMillis();
        var sizes = readScope.read(() -> gpsTrackDataPointRepository.findSegmentQualificationSizes(
                trackIds, GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED));
        List<Long> batch = new ArrayList<>();
        long batchPoints = 0;
        long totalPoints = 0;
        int batches = 0;
        for (var size : sizes) {
            if (!batch.isEmpty() && batchPoints + size.getPointCount() > QUALIFICATION_BATCH_POINT_BUDGET) {
                qualifyCandidateBatch(request, batch, candidatesPerZone, tracksPerZone);
                batch = new ArrayList<>();
                batchPoints = 0;
                batches++;
            }
            // A single dense track stays whole and is processed without other tracks in its batch.
            batch.add(size.getTrackId());
            batchPoints += size.getPointCount();
            totalPoints += size.getPointCount();
        }
        if (!batch.isEmpty()) {
            qualifyCandidateBatch(request, batch, candidatesPerZone, tracksPerZone);
            batches++;
        }
        log.info("Segment zone qualification completed: candidateTracks={} pointRows={} batches={} durationMs={}",
                trackIds.size(), totalPoints, batches, System.currentTimeMillis() - started);
    }

    private void qualifyCandidateBatch(CrossingPointsRequest request, List<Long> trackIds,
                                       Map<String, Set<Long>> candidatesPerZone, Map<String, Integer> tracksPerZone) {
        var visits = readScope.read(() -> {
            Map<Long, List<GpsTrackDataPoint>> pointsPerTrack = new LinkedHashMap<>();
            for (var row : gpsTrackDataPointRepository.findSegmentQualificationPoints(trackIds, GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED)) {
                var point = new GpsTrackDataPoint();
                point.setPointIndex(row.getPointIndex());
                point.setPointLongLat(row.getPointLongLat());
                point.setPointTimestamp(row.getPointTimestamp());
                point.setIntervalStatus(row.getIntervalStatus());
                pointsPerTrack.computeIfAbsent(row.getTrackId(), ignored -> new ArrayList<>()).add(point);
            }
            Map<Long, List<GpsTrackEvent>> eventsPerTrack = new HashMap<>();
            for (var row : gpsTrackEventRepository.findSegmentQualificationGaps(trackIds, GpsTrackEvent.EVENT_TYPE.GPS_GAP)) {
                var gap = new GpsTrackEvent();
                gap.setEventType(GpsTrackEvent.EVENT_TYPE.GPS_GAP);
                gap.setEndPointIndex(row.getEndPointIndex());
                eventsPerTrack.computeIfAbsent(row.getTrackId(), ignored -> new ArrayList<>()).add(gap);
            }
            Map<Long, Set<String>> answer = new LinkedHashMap<>();
            pointsPerTrack.forEach((id, points) -> answer.put(id, qualifyOtherTrack(request, id, candidatesPerZone,
                    points, eventsPerTrack.getOrDefault(id, List.of()))));
            return answer;
        });
        visits.values().forEach(visited -> visited.forEach(name -> tracksPerZone.compute(name, (ignored, count) -> count + 1)));
    }

    private Set<String> qualifyOtherTrack(CrossingPointsRequest request, Long id, Map<String, Set<Long>> candidatesPerZone,
                                          List<GpsTrackDataPoint> points, List<GpsTrackEvent> events) {
        var sections = recordingSections(points, events);
        Set<String> visited = new HashSet<>();
        for (var marker : request.triggerPoints) {
            if (candidatesPerZone.get(marker.name).contains(id)
                && sections.stream().anyMatch(section -> qualifies(section, marker, request.radius)))
                visited.add(marker.name);
        }
        return visited;
    }

    private static boolean qualifies(List<Sample> section, TriggerPoint marker, double radius) {
        double previous = distanceTo(section.getFirst().point(), marker.coordinate);
        if (previous <= radius) return true;
        for (int i = 1; i < section.size(); i++) {
            var from = section.get(i - 1);
            var to = section.get(i);
            double next = distanceTo(to.point(), marker.coordinate);
            if (closest(from, to, marker.coordinate, previous, next, radius).distance() <= radius) return true;
            previous = next;
        }
        return false;
    }

    private CrossingsPerTrack processTrack(CrossingPointsRequest request, Long trackId, TrackSummaryCalculator summaries) {
        long started = System.currentTimeMillis();
        var track = gpsTrackRepository.findById(trackId).orElse(null);
        if (track == null) {
            log.warn("Segment calculation skipped deleted candidate trackId={}", trackId);
            return null;
        }
        if (track.isTimingUnreliable()) {
            log.debug("Segment geometry only: trackId={} source timing is unsupported", trackId);
        }
        Long dataId = gpsTrackVariantSelector.forMetricsId(trackId);
        List<GpsTrackDataPoint> points = dataId == null ? List.of()
                : gpsTrackDataPointRepository.findAllByGpsTrackDataIdOrderByPointIndexAsc(dataId);
        var events = gpsTrackEventRepository.findAllByGpsTrackIdOrderByStartPointIndexAsc(trackId);
        List<Crossing> crossings = new ArrayList<>();
        var sections = recordingSections(points, events);
        for (int section = 0; section < sections.size(); section++) {
            for (TriggerPoint marker : request.triggerPoints) {
                findVisits(sections.get(section), marker, request.radius, trackId, section, crossings);
            }
        }
        // Time is an observed attribute, not a replacement for the recorded path order.
        crossings.sort(Comparator.comparingDouble((Crossing c) -> c.trackPointPosition)
                .thenComparing(c -> c.triggerPoint.name));
        markAmbiguousDwells(crossings, sections);
        if (track.isTimingUnreliable()) {
            crossings.forEach(c -> {
                c.gpsTrackDataPoint.setPointTimestamp(null);
                c.gpsTrackDataPoint.setDurationSinceStart(null);
            });
        }
        recomputeCrossingDeltas(crossings, points);
        annotateStops(crossings, events, points);
        var result = new CrossingsPerTrack(track, crossings);
        var zoneDigest = com.x8ing.mtl.server.mtlserver.metrics.CanonicalTrackRevision.digest();
        com.x8ing.mtl.server.mtlserver.metrics.CanonicalTrackRevision.add(zoneDigest, request.radius);
        request.triggerPoints.stream().sorted(Comparator.comparing(marker -> marker.name)).forEach(marker ->
                com.x8ing.mtl.server.mtlserver.metrics.CanonicalTrackRevision.add(zoneDigest, marker.name, marker.coordinate.x, marker.coordinate.y));
        result.setZoneRevision(HexFormat.of().formatHex(zoneDigest.digest()));
        result.setSourceRevision(com.x8ing.mtl.server.mtlserver.metrics.CanonicalTrackRevision.of(track, points, events));
        if (crossings.stream().map(c -> c.triggerPoint.name).distinct().count() == request.triggerPoints.size()) {
            summaries.summarize(result, points, events);
        }
        log.info("Segment track calculation completed: trackId={} points={} sections={} crossings={} attempts={} durationMs={}",
                trackId, points.size(), sections.size(), crossings.size(), result.getAttemptSummaries().size(),
                System.currentTimeMillis() - started);
        return result;
    }

    private static List<List<Sample>> recordingSections(List<GpsTrackDataPoint> points, List<GpsTrackEvent> events) {
        List<List<Sample>> sections = new ArrayList<>();
        List<Sample> current = new ArrayList<>();
        NavigableSet<Integer> gapEnds = new TreeSet<>();
        events.stream().filter(event -> event.getEventType() == GpsTrackEvent.EVENT_TYPE.GPS_GAP)
                .map(GpsTrackEvent::getEndPointIndex).filter(Objects::nonNull).forEach(gapEnds::add);
        gapEnds.addAll(RecordingContinuity.timeBreakPointIndexes(points, events));
        for (int i = 0; i < points.size(); i++) {
            GpsTrackDataPoint point = points.get(i);
            double position = point.getPointIndex() == null ? i : point.getPointIndex();
            boolean split = false;
            if (!current.isEmpty()) {
                GpsTrackDataPoint previous = current.getLast().point();
                double previousPosition = current.getLast().position();
                Integer nextGap = gapEnds.higher((int) previousPosition);
                split = nextGap != null && nextGap <= position;
                if (!split) split = !RecordingContinuity.hasGeometrySupport(previous, point);
            }
            if (split) {
                sections.add(current);
                current = new ArrayList<>();
            }
            current.add(new Sample(point, position));
        }
        if (!current.isEmpty()) sections.add(current);
        return sections;
    }

    private static void findVisits(List<Sample> points, TriggerPoint marker, double radius, Long trackId,
                                   int section, List<Crossing> crossings) {
        Sample first = points.getFirst();
        double previousDistance = distanceTo(first.point(), marker.coordinate);
        double exitRadius = radius + Math.max(MIN_ZONE_EXIT_MARGIN_M, radius * ZONE_EXIT_MARGIN_FRACTION);
        Candidate best = previousDistance <= radius ? new Candidate(first, first, 0, previousDistance) : null;
        for (int i = 1; i < points.size(); i++) {
            Sample from = points.get(i - 1), to = points.get(i);
            double nextDistance = distanceTo(to.point(), marker.coordinate);
            Candidate candidate = closest(from, to, marker.coordinate, previousDistance, nextDistance, radius);
            if (best == null) {
                if (candidate.distance() <= radius) best = candidate;
            } else {
                // Stable ties retain arrival at stationary anchors and repeated samples.
                if (candidate.distance() < best.distance() - GEOMETRY_TOLERANCE_M) best = candidate;
            }
            if (best != null && nextDistance > exitRadius) {
                crossings.add(crossing(best, marker, trackId, section));
                best = null;
            }
            previousDistance = nextDistance;
        }
        if (best != null) crossings.add(crossing(best, marker, trackId, section));
    }

    /**
     * A confirmed nonzero wait does not identify one precise passage time through its interior.
     */
    private static void markAmbiguousDwells(List<Crossing> crossings, List<List<Sample>> sections) {
        List<Dwell> dwells = new ArrayList<>();
        for (var section : sections) {
            int first = 1;
            while (first < section.size()) {
                if (section.get(first).point().getMotionState() != TrackMotionAnalyzer.State.STOPPED) {
                    first++;
                    continue;
                }
                int end = first;
                Sample start = section.get(first - 1);
                while (end < section.size() && section.get(end).point().getMotionState() == TrackMotionAnalyzer.State.STOPPED) {
                    end++;
                }
                Double fromTime = seconds(start.point()), toTime = seconds(section.get(end - 1).point());
                if (fromTime != null && toTime != null && toTime > fromTime)
                    dwells.add(new Dwell(start.position(), section.get(end - 1).position()));
                first = end;
            }
        }
        int dwellIndex = 0;
        for (var crossing : crossings) {
            while (dwellIndex < dwells.size() && dwells.get(dwellIndex).end() < crossing.trackPointPosition) dwellIndex++;
            crossing.timingAmbiguous = dwellIndex < dwells.size() && dwells.get(dwellIndex).start() <= crossing.trackPointPosition;
        }
    }

    /**
     * Reconstruct signed canonical positions through the same boundary and timing rules as discovery.
     */
    public static List<Crossing> crossingsAtPositions(List<GpsTrackDataPoint> points, List<GpsTrackEvent> events,
                                                      double startPosition, double endPosition, Long trackId,
                                                      boolean timingUnreliable) {
        var sections = recordingSections(points, events);
        List<Crossing> crossings = new ArrayList<>();
        for (double position : new double[]{startPosition, endPosition}) {
            Crossing boundary = null;
            for (int section = 0; section < sections.size() && boundary == null; section++) {
                var samples = sections.get(section);
                for (int i = 0; i < samples.size(); i++) {
                    var right = samples.get(i);
                    if (right.position() == position) {
                        boundary = crossing(new Candidate(right, right, 0, 0), null, trackId, section);
                        break;
                    }
                    if (i > 0 && right.position() > position && samples.get(i - 1).position() < position) {
                        var left = samples.get(i - 1);
                        double factor = (position - left.position()) / (right.position() - left.position());
                        boundary = crossing(new Candidate(left, right, factor, 0), null, trackId, section);
                        break;
                    }
                }
            }
            if (boundary == null) throw new IllegalArgumentException("Unsupported segment boundary");
            crossings.add(boundary);
        }
        markAmbiguousDwells(crossings, sections);
        if (timingUnreliable) crossings.forEach(c -> c.gpsTrackDataPoint.setPointTimestamp(null));
        recomputeCrossingDeltas(crossings, points);
        return List.copyOf(crossings);
    }

    /**
     * Minimize ellipsoidal distance along the same wrapped lon/lat curve used for the emitted point.
     */
    private static Candidate closest(Sample from, Sample to, Coordinate marker, double d1, double d2, double radius) {
        double length = distance(from.point(), to.point());
        if (length <= GEOMETRY_TOLERANCE_M || d1 <= GEOMETRY_TOLERANCE_M)
            return new Candidate(from, to, 0, d1);
        if (d2 <= GEOMETRY_TOLERANCE_M) return new Candidate(from, to, 1, d2);
        // This conservative bound avoids the iterative search for distant markers.
        if (Math.min(d1, d2) > radius + length * 2) return d1 <= d2
                ? new Candidate(from, to, 0, d1) : new Candidate(from, to, 1, d2);
        double lo = 0, hi = 1;
        double left = hi - GOLDEN_RATIO_COMPLEMENT * (hi - lo);
        double right = lo + GOLDEN_RATIO_COMPLEMENT * (hi - lo);
        double dl = distanceAt(from, to, marker, left), dr = distanceAt(from, to, marker, right);
        for (int i = 0; i < MINIMUM_SEARCH_ITERATIONS; i++) {
            if (dl <= dr) {
                hi = right;
                right = left;
                dr = dl;
                left = hi - GOLDEN_RATIO_COMPLEMENT * (hi - lo);
                dl = distanceAt(from, to, marker, left);
            } else {
                lo = left;
                left = right;
                dl = dr;
                right = lo + GOLDEN_RATIO_COMPLEMENT * (hi - lo);
                dr = distanceAt(from, to, marker, right);
            }
        }
        double factor = dl <= dr ? left : right;
        double d = Math.min(dl, dr);
        if (d1 <= d) return new Candidate(from, to, 0, d1);
        if (d2 <= d) return new Candidate(from, to, 1, d2);
        return new Candidate(from, to, factor, d);
    }

    private static double distanceAt(Sample from, Sample to, Coordinate marker, double factor) {
        Coordinate point = coordinateAt(from.point(), to.point(), factor);
        return Geodesic.WGS84.Inverse(marker.y, marker.x, point.y, point.x).s12;
    }

    private static Coordinate coordinateAt(GpsTrackDataPoint from, GpsTrackDataPoint to, double factor) {
        return RecordedPathGeometry.interpolate(
                from.getPointLongLat().getCoordinate(), to.getPointLongLat().getCoordinate(), factor);
    }

    private static Crossing crossing(Candidate candidate, TriggerPoint marker, Long trackId, int section) {
        var from = candidate.from().point();
        var to = candidate.to().point();
        double factor = candidate.factor();
        var anchor = factor <= 0.5 ? from : to;
        var point = new GpsTrackDataPoint();
        point.setId(anchor.getId());
        point.setGpsTrackDataId(anchor.getGpsTrackDataId());
        point.setPointIndex(anchor.getPointIndex());
        point.setPointIndexMax(anchor.getPointIndexMax());
        point.setPointLongLat(GEOMETRY.createPoint(coordinateAt(from, to, factor)));
        if (factor == 0) point.setPointTimestamp(from.getPointTimestamp());
        else if (factor == 1) point.setPointTimestamp(to.getPointTimestamp());
        else if (from.getPointTimestamp() != null && to.getPointTimestamp() != null
                 && to.getPointTimestamp().after(from.getPointTimestamp())) {
            long start = from.getPointTimestamp().getTime();
            point.setPointTimestamp(new Date(start + Math.round((to.getPointTimestamp().getTime() - start) * factor)));
        }
        point.setPointAltitude(interpolate(from.getPointAltitude(), to.getPointAltitude(), factor));
        point.setDistanceInMeterSinceStart(interpolate(from.getDistanceInMeterSinceStart(), to.getDistanceInMeterSinceStart(), factor));
        point.setDurationSinceStart(interpolate(from.getDurationSinceStart(), to.getDurationSinceStart(), factor));
        var result = new Crossing();
        result.triggerPoint = marker;
        result.gpsTrackId = trackId;
        result.gpsTrackDataPoint = point;
        result.distanceToTriggerPointInMeter = candidate.distance();
        result.trackPointPosition = candidate.from().position() + (candidate.to().position() - candidate.from().position()) * factor;
        result.recordingSectionIndex = section;
        return result;
    }

    private static Double interpolate(Double from, Double to, double factor) {
        if (factor == 0) return from;
        if (factor == 1) return to;
        return from == null || to == null ? null : from + (to - from) * factor;
    }

    static void recomputeCrossingDeltas(List<Crossing> crossings, List<GpsTrackDataPoint> points) {
        if (crossings == null || crossings.isEmpty()) return;
        NavigableSet<Double> clockResets = new TreeSet<>();
        Double known = null;
        for (var point : points) {
            Double time = seconds(point);
            if (time != null) {
                if (known != null && time < known) clockResets.add(point.getPointIndex().doubleValue());
                known = time;
            }
        }
        int pointCursor = 0;
        for (int i = 0; i < crossings.size(); i++) {
            Crossing current = crossings.get(i);
            current.timeInSecSinceLastTriggerPoint = null;
            current.distanceInMeterSinceLastTriggerPoint = null;
            current.avgSpeedSinceLastTriggerPoint = null;
            current.recordingGapSinceLastTriggerPoint = false;
            current.timingStatusSinceLastTriggerPoint = SegmentTimingStatus.NO_PREVIOUS_CROSSING;
            if (i == 0) continue;
            Crossing previous = crossings.get(i - 1);
            current.recordingGapSinceLastTriggerPoint = previous.recordingSectionIndex != current.recordingSectionIndex;
            Double fromPosition = position(previous), toPosition = position(current);
            if (fromPosition == null || toPosition == null || toPosition < fromPosition) {
                current.timingStatusSinceLastTriggerPoint = SegmentTimingStatus.INVALID_TIME;
                continue;
            }
            double meters = 0;
            if (!current.recordingGapSinceLastTriggerPoint) {
                GpsTrackDataPoint last = previous.gpsTrackDataPoint;
                while (pointCursor < points.size() && (points.get(pointCursor).getPointIndex() == null
                                                       || points.get(pointCursor).getPointIndex() <= fromPosition)) pointCursor++;
                while (pointCursor < points.size() && points.get(pointCursor).getPointIndex() < toPosition) {
                    GpsTrackDataPoint point = points.get(pointCursor++);
                    meters += distance(last, point);
                    last = point;
                }
                meters += distance(last, current.gpsTrackDataPoint);
                current.distanceInMeterSinceLastTriggerPoint = meters;
            }
            Double fromTime = seconds(previous.gpsTrackDataPoint), toTime = seconds(current.gpsTrackDataPoint);
            Double reset = clockResets.higher(fromPosition);
            if (fromTime == null || toTime == null) {
                current.timingStatusSinceLastTriggerPoint = SegmentTimingStatus.MISSING_TIME;
            } else if ((reset != null && reset <= toPosition) || toTime < fromTime
                       || (toTime.equals(fromTime) && toPosition > fromPosition)) {
                current.timingStatusSinceLastTriggerPoint = SegmentTimingStatus.INVALID_TIME;
            } else if (previous.timingAmbiguous || current.timingAmbiguous) {
                current.timingStatusSinceLastTriggerPoint = SegmentTimingStatus.AMBIGUOUS_DWELL;
            } else {
                current.timingStatusSinceLastTriggerPoint = SegmentTimingStatus.AVAILABLE;
                double elapsed = (current.gpsTrackDataPoint.getPointTimestamp().getTime()
                                  - previous.gpsTrackDataPoint.getPointTimestamp().getTime()) / MILLIS_PER_SECOND;
                current.timeInSecSinceLastTriggerPoint = elapsed;
                if (elapsed > 0 && current.distanceInMeterSinceLastTriggerPoint != null)
                    current.avgSpeedSinceLastTriggerPoint = meters / elapsed * MPS_TO_KMH;
            }
        }
    }

    private static Double position(Crossing crossing) {
        if (crossing.trackPointPosition != null) return crossing.trackPointPosition;
        return crossing.gpsTrackDataPoint == null || crossing.gpsTrackDataPoint.getPointIndex() == null
                ? null : crossing.gpsTrackDataPoint.getPointIndex().doubleValue();
    }

    private static Double seconds(GpsTrackDataPoint point) {
        return point == null || point.getPointTimestamp() == null ? null : point.getPointTimestamp().getTime() / MILLIS_PER_SECOND;
    }

    private static double distance(GpsTrackDataPoint from, GpsTrackDataPoint to) {
        return GPXReader.getDistanceBetweenTwoWGS84(from.getPointLongLat().getCoordinate(), to.getPointLongLat().getCoordinate());
    }

    private static double distanceTo(GpsTrackDataPoint point, Coordinate marker) {
        return GPXReader.getDistanceBetweenTwoWGS84(marker, point.getPointLongLat().getCoordinate());
    }

    private record StationarySpan(double startPosition, double endPosition, long startTime, long endTime) {
        double seconds() {
            return (endTime - startTime) / MILLIS_PER_SECOND;
        }
    }

    private static void annotateStops(List<Crossing> crossings, List<GpsTrackEvent> events, List<GpsTrackDataPoint> points) {
        List<StationarySpan> spans = new ArrayList<>();
        NavigableSet<Integer> gaps = new TreeSet<>(RecordingContinuity.timeBreakPointIndexes(points, events));
        events.stream().filter(e -> e.getEventType() == GpsTrackEvent.EVENT_TYPE.GPS_GAP)
                .map(GpsTrackEvent::getEndPointIndex).filter(Objects::nonNull).forEach(gaps::add);
        for (int i = 1; i < points.size(); i++) {
            var a = points.get(i - 1);
            var b = points.get(i);
            Integer gap = gaps.higher(a.getPointIndex());
            if (b.getMotionState() != TrackMotionAnalyzer.State.STOPPED || a.getPointTimestamp() == null
                || b.getPointTimestamp() == null || !b.getPointTimestamp().after(a.getPointTimestamp())
                || (gap != null && gap <= b.getPointIndex()) || !RecordingContinuity.hasGeometrySupport(a, b)) continue;
            var span = new StationarySpan(a.getPointIndex(), b.getPointIndex(),
                    a.getPointTimestamp().getTime(), b.getPointTimestamp().getTime());
            if (!spans.isEmpty() && spans.getLast().endPosition() == span.startPosition()
                && spans.getLast().endTime() == span.startTime()) {
                var previous = spans.removeLast();
                span = new StationarySpan(previous.startPosition(), span.endPosition(), previous.startTime(), span.endTime());
            }
            spans.add(span);
        }
        int spanCursor = 0, intervalCursor = 1;
        for (int i = 1; i < crossings.size(); i++) {
            var previous = crossings.get(i - 1);
            var current = crossings.get(i);
            if (current.timingStatusSinceLastTriggerPoint != SegmentTimingStatus.AVAILABLE
                && current.timingStatusSinceLastTriggerPoint != SegmentTimingStatus.AMBIGUOUS_DWELL) continue;
            double fromPosition = position(previous), toPosition = position(current);
            long from = previous.gpsTrackDataPoint.getPointTimestamp().getTime();
            long to = current.gpsTrackDataPoint.getPointTimestamp().getTime();
            var notes = new SegmentNotes(0, 0, 0, !current.recordingGapSinceLastTriggerPoint
                                                  && current.timingStatusSinceLastTriggerPoint == SegmentTimingStatus.AVAILABLE);
            while (spanCursor < spans.size() && spans.get(spanCursor).endPosition() <= fromPosition) spanCursor++;
            for (int j = spanCursor; j < spans.size() && spans.get(j).startPosition() < toPosition; j++) {
                var span = spans.get(j);
                double overlap = Math.max(0, Math.min(to, span.endTime()) - Math.max(from, span.startTime())) / MILLIS_PER_SECOND;
                notes.totalStoppedSec += overlap;
                if (overlap > 0 && span.seconds() >= TrackMotionAnalyzer.MIN_VISIBLE_BREAK_DURATION_S) {
                    notes.stopCount++;
                    notes.longestStopSec = Math.max(notes.longestStopSec, overlap);
                }
            }
            while (intervalCursor < points.size() && points.get(intervalCursor).getPointIndex() <= fromPosition) intervalCursor++;
            for (int j = intervalCursor; j < points.size() && points.get(j - 1).getPointIndex() < toPosition; j++) {
                if (points.get(j).getMotionState() == null || points.get(j).getMotionState() == TrackMotionAnalyzer.State.UNKNOWN)
                    notes.coverageComplete = false;
            }
            current.segmentNotesSinceLastTriggerPoint = notes;
        }
    }

    private static void validate(CrossingPointsRequest request) {
        if (request == null || request.radius == null || !Double.isFinite(request.radius) || request.radius <= 0)
            throw new IllegalArgumentException("A finite positive radius is required.");
        if (request.triggerPoints == null || request.triggerPoints.isEmpty())
            throw new IllegalArgumentException("Trigger points are required.");
        Set<String> names = new HashSet<>();
        for (var marker : request.triggerPoints) {
            if (marker == null || marker.name == null || marker.name.isBlank() || !names.add(marker.name)
                || marker.coordinate == null || !Double.isFinite(marker.coordinate.x) || !Double.isFinite(marker.coordinate.y)
                || Math.abs(marker.coordinate.x) > 180 || Math.abs(marker.coordinate.y) > 90)
                throw new IllegalArgumentException("Trigger points need unique names and valid WGS84 coordinates.");
        }
    }

    private record MarkerPair(String from, String to) {
    }

    private record Sample(GpsTrackDataPoint point, double position) {
    }

    private record Candidate(Sample from, Sample to, double factor, double distance) {
    }

    private record Dwell(double start, double end) {
    }
}
