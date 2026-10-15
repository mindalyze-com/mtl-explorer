package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.gpx.RecordingContinuity;
import com.x8ing.mtl.server.mtlserver.logic.crossing.TrackTimeBetweenTwoPoints;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.Crossing;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsResponse;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingsPerTrack;
import com.x8ing.mtl.server.mtlserver.metrics.CanonicalTrackRevision;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingPointsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

/**
 * The only path from an untrusted attempt reference to exact canonical boundaries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentAttemptResolver {
    private static final int MAX_BATCH_ATTEMPTS = 1_000;
    private final GpsTrackRepository tracks;
    private final GpsTrackVariantSelector variants;
    private final GpsTrackEventRepository events;
    private final SegmentAttemptTokens tokens;

    public record ResolvedAttempt(SegmentAttemptSummary summary, GpsTrack track,
                                  List<GpsTrackDataPoint> points, List<GpsTrackEvent> events, NavigableSet<Integer> gapEnds) {
        public ResolvedAttempt(SegmentAttemptSummary summary, GpsTrack track,
                               List<GpsTrackDataPoint> points, List<GpsTrackEvent> events) {
            this(summary, track, points, events, recordingGaps(points, events));
        }

        public SegmentAttemptIdentity identity() {
            return summary.identity();
        }
    }

    /**
     * Complete source-dependent calculations while only this track's points are loaded.
     */
    public static void prepare(CrossingsPerTrack track,
                               List<GpsTrackDataPoint> points, List<GpsTrackEvent> events) {
        var gapEnds = recordingGaps(points, events);
        List<SegmentAttemptSummary> summaries = new ArrayList<>();
        Map<String, Integer> visits = new HashMap<>();
        int previousVisit = 0;
        var crossings = track.getCrossings();
        for (int i = 0; i < crossings.size(); i++) {
            var end = crossings.get(i);
            int visit = visits.merge(end.triggerPoint.name, 1, Integer::sum);
            end.visitNumber = visit;
            if (i > 0) {
                var start = crossings.get(i - 1);
                var identity = new SegmentAttemptIdentity(null, track.gpsTrack.getId(),
                        start.triggerPoint.name, previousVisit, end.triggerPoint.name, visit,
                        instant(start.gpsTrackDataPoint.getPointTimestamp()), instant(end.gpsTrackDataPoint.getPointTimestamp()),
                        start.trackPointPosition, end.trackPointPosition,
                        coordinates(start.gpsTrackDataPoint), coordinates(end.gpsTrackDataPoint),
                        track.getSourceRevision(), track.getZoneRevision(), end.timingStatusSinceLastTriggerPoint);
                var summary = new SegmentAttemptSummary(identity, end.distanceInMeterSinceLastTriggerPoint,
                        end.timeInSecSinceLastTriggerPoint, end.avgSpeedSinceLastTriggerPoint,
                        null, null, null, null, Map.of(
                        "distance", metric(end.distanceInMeterSinceLastTriggerPoint,
                                end.recordingGapSinceLastTriggerPoint ? "RECORDING_GAP" : "DISTANCE_UNAVAILABLE"),
                        "elapsed", metric(end.timeInSecSinceLastTriggerPoint, end.timingStatusSinceLastTriggerPoint.name()),
                        "speed", metric(end.avgSpeedSinceLastTriggerPoint,
                                end.recordingGapSinceLastTriggerPoint ? "RECORDING_GAP" : end.timingStatusSinceLastTriggerPoint.name())));
                // Validate before publishing so discovery tables and detailed analysis cannot disagree.
                summary = SegmentCoreCalculator.calculate(new ResolvedAttempt(summary, track.gpsTrack,
                        points, events, gapEnds)).summary();
                end.distanceInMeterSinceLastTriggerPoint = summary.distanceMeters();
                end.avgSpeedSinceLastTriggerPoint = summary.averageSpeedKmh();
                summaries.add(summary);
            }
            previousVisit = visit;
        }
        track.setAttemptSummaries(List.copyOf(summaries));
    }

    public void publish(CrossingPointsResponse source, CrossingPointsResponseDto response) {
        long started = System.nanoTime();
        int attempts = 0;
        List<SegmentAttemptSummary> allSummaries = new ArrayList<>();
        log.info("Segment attempts publishing: tracks={}", source.crossings.size());
        for (var entry : source.crossings.entrySet()) {
            var summaries = entry.getValue().getAttemptSummaries().stream()
                    .map(summary -> tokens.issue(summary, owner())).toList();
            var result = response.getCrossings().get(entry.getKey());
            result.setAttempts(summaries);
            result.setSegmentSummaries(SegmentConsolidation.summarize(summaries));
            allSummaries.addAll(summaries);
            attempts += summaries.size();
        }
        source.segmentsStats = SegmentConsolidation.timingStatistics(allSummaries);
        response.setSegmentsStats(source.segmentsStats);
        log.info("Segment attempts published: attempts={} durationMs={}", attempts, elapsedMillis(started));
    }

    @Transactional(readOnly = true)
    public ResolvedAttempt resolve(String reference) {
        long started = System.nanoTime();
        log.info("Segment attempt resolution starting");
        try {
            var descriptor = tokens.read(reference, owner());
            var track = tracks.findById(descriptor.trackId()).orElseThrow(SegmentAttemptResolver::stale);
            var canonical = variants.pointsForMetrics(descriptor.trackId());
            var trackEvents = events.findAllByGpsTrackIdOrderByStartPointIndexAsc(descriptor.trackId());
            if (!Objects.equals(descriptor.revision(), CanonicalTrackRevision.of(track, canonical, trackEvents))) throw stale();
            var restored = restore(descriptor, reference, track, canonical, trackEvents);
            log.info("Segment attempt resolution completed: trackId={} points={} durationMs={}",
                    descriptor.trackId(), canonical.size(), elapsedMillis(started));
            return restored;
        } catch (ResponseStatusException exception) {
            log.warn("Segment attempt rejected: type={} reason={}", exception.getClass().getSimpleName(), exception.getReason());
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Segment attempt resolution failed: type={}", exception.getClass().getSimpleName(), exception);
            throw exception;
        }
    }

    /**
     * Request-local reuse only; each track is read and revision-checked once for this batch.
     */
    @Transactional(readOnly = true)
    public List<ResolvedAttempt> resolveAll(List<String> requested) {
        if (requested == null || requested.isEmpty() || requested.size() > MAX_BATCH_ATTEMPTS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supply between 1 and 1000 attempt references");
        Map<Long, ResolvedAttempt> sources = new HashMap<>();
        List<ResolvedAttempt> result = new ArrayList<>();
        for (String reference : new LinkedHashSet<>(requested)) {
            var descriptor = tokens.read(reference, owner());
            var source = sources.get(descriptor.trackId());
            if (source == null) {
                source = resolve(reference);
                sources.put(descriptor.trackId(), source);
                result.add(source);
            } else {
                if (!Objects.equals(source.identity().revision(), descriptor.revision())) throw stale();
                result.add(restore(descriptor, reference, source.track(), source.points(), source.events()));
            }
        }
        return List.copyOf(result);
    }

    private static ResolvedAttempt restore(SegmentAttemptTokens.Descriptor descriptor, String reference, GpsTrack track,
                                           List<GpsTrackDataPoint> points, List<GpsTrackEvent> events) {
        if (points.isEmpty() || descriptor.startPosition() < points.getFirst().getPointIndex()
            || descriptor.endPosition() > points.getLast().getPointIndex()) throw stale();
        List<Crossing> boundaries;
        try {
            boundaries = TrackTimeBetweenTwoPoints.crossingsAtPositions(points, events, descriptor.startPosition(),
                    descriptor.endPosition(), descriptor.trackId(), track.isTimingUnreliable());
        } catch (IllegalArgumentException exception) {
            log.warn("Segment boundary restoration rejected: trackId={} type={} reason={}",
                    descriptor.trackId(), exception.getClass().getSimpleName(), exception.getMessage());
            throw stale();
        }
        var start = boundaries.getFirst();
        var end = boundaries.getLast();
        var identity = new SegmentAttemptIdentity(reference, descriptor.trackId(), descriptor.fromZone(), descriptor.fromVisit(),
                descriptor.toZone(), descriptor.toVisit(), instant(start.gpsTrackDataPoint.getPointTimestamp()),
                instant(end.gpsTrackDataPoint.getPointTimestamp()), descriptor.startPosition(), descriptor.endPosition(),
                coordinates(start.gpsTrackDataPoint), coordinates(end.gpsTrackDataPoint), descriptor.revision(),
                descriptor.zoneRevision(), end.timingStatusSinceLastTriggerPoint);
        var summary = new SegmentAttemptSummary(identity, end.distanceInMeterSinceLastTriggerPoint,
                end.timeInSecSinceLastTriggerPoint, end.avgSpeedSinceLastTriggerPoint,
                null, null, null, null, Map.of());
        var resolved = new ResolvedAttempt(summary, track, points, events);
        return new ResolvedAttempt(SegmentCoreCalculator.calculate(resolved).summary(), track, points, events, resolved.gapEnds());
    }

    private static NavigableSet<Integer> recordingGaps(List<GpsTrackDataPoint> points, List<GpsTrackEvent> events) {
        var gaps = new TreeSet<>(RecordingContinuity.timeBreakPointIndexes(points, events));
        events.stream().filter(event -> event.getEventType() == GpsTrackEvent.EVENT_TYPE.GPS_GAP)
                .map(GpsTrackEvent::getEndPointIndex).filter(Objects::nonNull).forEach(gaps::add);
        return Collections.unmodifiableNavigableSet(gaps);
    }

    private static SegmentMetricAvailability metric(Double value, String reason) {
        return new SegmentMetricAvailability(value == null ? SegmentMetricAvailability.State.UNAVAILABLE : SegmentMetricAvailability.State.AVAILABLE,
                value == null ? reason : null, null);
    }

    static ResponseStatusException stale() {
        return new ResponseStatusException(HttpStatus.GONE, "Attempt expired or track changed. Refresh the segment results.");
    }

    private static String owner() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }

    private static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private static List<Double> coordinates(GpsTrackDataPoint point) {
        return List.of(point.getPointLongLat().getX(), point.getPointLongLat().getY());
    }

    static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
