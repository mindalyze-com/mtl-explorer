package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.SegmentCardiacCostRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentHeartRateCalculator;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentHeartRateGroup;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentHeartRateSummary;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentTimingStatus;
import com.x8ing.mtl.server.mtlserver.metrics.CanonicalTrackRevision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Optional table HR uses all requested visits and one raw timeline, with server-owned covered-time aggregation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentTrackHeartRateService {
    private final SegmentAttemptResolver resolver;
    private final SegmentCoreService core;
    private final SegmentCardiacCostRepository repository;

    private record ZonePair(String from, String to) {
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SegmentTrackHeartRate get(List<String> references) {
        long started = System.nanoTime();
        log.info("Segment table heart rate starting: attempts={}", references == null ? 0 : references.size());
        try {
            var attempts = resolver.resolveAll(references);
            var identity = attempts.getFirst().identity();
            if (attempts.stream().anyMatch(a -> a.identity().trackId() != identity.trackId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Table heart rate requests must belong to one track");
            var valid = attempts.stream().filter(a -> a.identity().timingStatus() == SegmentTimingStatus.AVAILABLE
                                                      && a.identity().start() != null && a.identity().end() != null && a.identity().end().isAfter(a.identity().start())).toList();
            if (valid.isEmpty()) return new SegmentTrackHeartRate(identity.trackId(), identity.revision(), null, List.of(), List.of());
            var start = valid.stream().map(a -> a.identity().start()).min(Comparator.naturalOrder()).orElseThrow();
            var end = valid.stream().map(a -> a.identity().end()).max(Comparator.naturalOrder()).orElseThrow();
            var samples = repository.heartRate(start, end);
            var gaps = repository.recordingGaps(identity.trackId(), start, end);
            List<SegmentAttemptHeartRate> summaries = new ArrayList<>();
            Map<ZonePair, List<SegmentHeartRateSummary>> groups = new LinkedHashMap<>();
            for (var attempt : valid) {
                var selected = attempt.identity();
                var summary = SegmentHeartRateCalculator.calculateOverRecordedRanges(selected.start(), selected.end(),
                        SegmentHealthSeries.recordedRanges(core.full(attempt)), samples, gaps);
                summaries.add(new SegmentAttemptHeartRate(selected, summary));
                groups.computeIfAbsent(new ZonePair(selected.fromZone(), selected.toZone()), ignored -> new ArrayList<>()).add(summary);
            }
            var digest = CanonicalTrackRevision.digest();
            for (var sample : samples) CanonicalTrackRevision.add(digest, sample.timestamp(), sample.bpm());
            return new SegmentTrackHeartRate(identity.trackId(), identity.revision(), HexFormat.of().formatHex(digest.digest()),
                    List.copyOf(summaries), groups.entrySet().stream().map(g -> new SegmentHeartRateGroup(g.getKey().from(),
                    g.getKey().to(), SegmentHeartRateCalculator.combine(g.getValue()))).toList());
        } catch (RuntimeException exception) {
            log.error("Segment table heart rate failed: type={}", exception.getClass().getSimpleName(), exception);
            throw exception;
        } finally {
            log.info("Segment table heart rate finished: durationMs={}", SegmentAttemptResolver.elapsedMillis(started));
        }
    }
}
