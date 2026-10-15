package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.SegmentCardiacCostRepository;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentTimingStatus;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingPointsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentHeartRateService {
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;
    private final SegmentCardiacCostRepository repository;

    private record SegmentKey(String from, String to) {
    }

    @Transactional(readOnly = true)
    public void enrich(CrossingPointsResponseDto response) {
        long started = System.nanoTime();
        int attempts = 0;
        log.info("Segment heart rate starting: tracks={}", response.getCrossings().size());
        try {
            for (var entry : response.getCrossings().entrySet()) {
                var track = entry.getValue();
                var crossings = track.getCrossings();
                var times = crossings.stream().map(SegmentHeartRateService::timestamp).filter(Objects::nonNull).sorted().toList();
                if (times.size() < 2) continue;
                Instant start = times.getFirst();
                Instant end = times.getLast();
                var samples = repository.heartRate(start, end);
                // Read each track once; adjacent visits share the same raw sample and GPS timeline.
                var motion = samples.size() < 2 ? List.<SegmentCardiacCostCalculator.MotionInterval>of() : repository.motion(entry.getKey(), start, end);
                var gaps = samples.size() < 2 ? List.<SegmentCardiacCostCalculator.TimeRange>of() : repository.recordingGaps(entry.getKey(), start, end);
                Map<SegmentKey, List<SegmentHeartRateSummary>> groups = new LinkedHashMap<>();
                for (int i = 1; i < crossings.size(); i++) {
                    var previous = crossings.get(i - 1);
                    var current = crossings.get(i);
                    if (current.getTimingStatusSinceLastTriggerPoint() != null
                        && current.getTimingStatusSinceLastTriggerPoint() != SegmentTimingStatus.AVAILABLE) continue;
                    var summary = SegmentHeartRateCalculator.calculate(timestamp(previous), timestamp(current), motion, samples, gaps);
                    current.setHeartRateSinceLastTriggerPoint(summary);
                    attempts++;
                    if (previous.getTriggerPoint() != null && current.getTriggerPoint() != null) {
                        var key = new SegmentKey(previous.getTriggerPoint().name, current.getTriggerPoint().name);
                        groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(summary);
                    }
                }
                track.setSegmentHeartRateSummaries(groups.entrySet().stream().map(group ->
                        new SegmentHeartRateGroup(group.getKey().from(), group.getKey().to(), SegmentHeartRateCalculator.combine(group.getValue()))).toList());
            }
            log.info("Segment heart rate completed: tracks={}, attempts={}, durationMs={}", response.getCrossings().size(), attempts,
                    (System.nanoTime() - started) / NANOS_PER_MILLISECOND);
        } catch (RuntimeException exception) {
            log.error("Segment heart rate failed: tracks={}, completedAttempts={}, type={}", response.getCrossings().size(), attempts,
                    exception.getClass().getSimpleName(), exception);
            throw exception;
        }
    }

    private static Instant timestamp(CrossingDto crossing) {
        return crossing.getGpsTrackDataPoint() == null || crossing.getGpsTrackDataPoint().getPointTimestamp() == null
                ? null : crossing.getGpsTrackDataPoint().getPointTimestamp().toInstant();
    }
}
