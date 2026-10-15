package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.SegmentCardiacCostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentCardiacCostService {
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;
    private final GpsTrackRepository tracks;
    private final GpsTrackDataRepository variants;
    private final GpsTrackDataPointRepository points;
    private final SegmentCardiacCostRepository repository;

    @Transactional(readOnly = true)
    public SegmentCardiacCostSummary get(long trackId, long fromPointId, long toPointId) {
        long started = System.nanoTime();
        log.info("Segment cardiac cost starting: trackId={}, fromPointId={}, toPointId={}", trackId, fromPointId, toPointId);
        try {
            GpsTrack track = tracks.findById(trackId).orElseThrow(() -> notFound("Track not found"));
            GpsTrackDataPoint from = points.findById(fromPointId).orElseThrow(() -> notFound("Start point not found"));
            GpsTrackDataPoint to = points.findById(toPointId).orElseThrow(() -> notFound("End point not found"));
            if (from.getGpsTrackDataId() == null || !from.getGpsTrackDataId().equals(to.getGpsTrackDataId())
                || variants.findById(from.getGpsTrackDataId()).filter(data -> Long.valueOf(trackId).equals(data.getGpsTrackId())).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Segment endpoints must belong to the requested track and variant");
            }
            SegmentCardiacCostSummary result;
            if (SegmentCardiacCostCalculator.motorized(track.getActivityType())) {
                result = SegmentCardiacCostCalculator.unavailable(SegmentCardiacCostSummary.UnavailableReason.MOTORIZED_ACTIVITY);
            } else if (from.getPointTimestamp() == null || to.getPointTimestamp() == null) {
                result = SegmentCardiacCostCalculator.unavailable(SegmentCardiacCostSummary.UnavailableReason.MISSING_TIMESTAMPS);
            } else {
                Instant start = from.getPointTimestamp().toInstant();
                Instant end = to.getPointTimestamp().toInstant();
                if (!end.isAfter(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Segment end must be after its start");
                var motion = repository.motion(trackId, start, end);
                var samples = repository.heartRate(start, end);
                var breaks = repository.breaks(trackId, start, end);
                log.info("Segment cardiac cost data loaded: trackId={}, motionIntervals={}, heartRateSamples={}, breaks={}",
                        trackId, motion.size(), samples.size(), breaks.size());
                result = SegmentCardiacCostCalculator.calculate(start, end, motion, samples, breaks);
            }
            log.info("Segment cardiac cost completed: trackId={}, available={}, reason={}, coveredMovingSeconds={}, durationMs={}",
                    trackId, result.available(), result.unavailableReason(), result.coveredMovingSeconds(),
                    (System.nanoTime() - started) / NANOS_PER_MILLISECOND);
            return result;
        } catch (ResponseStatusException exception) {
            log.warn("Segment cardiac cost rejected: trackId={}, fromPointId={}, toPointId={}, type={}, reason={}",
                    trackId, fromPointId, toPointId, exception.getClass().getSimpleName(), exception.getReason());
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Segment cardiac cost failed: trackId={}, fromPointId={}, toPointId={}, type={}",
                    trackId, fromPointId, toPointId, exception.getClass().getSimpleName(), exception);
            throw exception;
        }
    }

    private static ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }
}
