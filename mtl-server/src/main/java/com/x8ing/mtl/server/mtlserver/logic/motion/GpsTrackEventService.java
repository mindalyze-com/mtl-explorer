package com.x8ing.mtl.server.mtlserver.logic.motion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.metrics.MetricConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
@JsonPropertyOrder({
        "gpsTrackEventRepository"
})
public class GpsTrackEventService {

    private static final double MILLIS_PER_SECOND = 1000;

    private final GpsTrackEventRepository gpsTrackEventRepository;

    public GpsTrackEventService(GpsTrackEventRepository gpsTrackEventRepository) {
        this.gpsTrackEventRepository = gpsTrackEventRepository;
    }

    /**
     * Replaces the generated stop events for a track while leaving future
     * user-created/manual events intact.
     */
    @Transactional
    public void replaceDetectedStopEvents(Long gpsTrackId, Long gpsTrackDataId, List<TrackStopDetector.PointStopRange> stopRanges) {
        if (gpsTrackId == null) {
            return;
        }
        long started = System.currentTimeMillis();
        log.info("Detected break persistence starting: trackId={} breaks={}", gpsTrackId, stopRanges == null ? 0 : stopRanges.size());
        gpsTrackEventRepository.deleteByGpsTrackIdAndEventTypeAndSource(
                gpsTrackId,
                GpsTrackEvent.EVENT_TYPE.STOP,
                GpsTrackEvent.SOURCE.DETECTED);
        if (stopRanges == null || stopRanges.isEmpty()) {
            log.info("Detected break persistence completed: trackId={} breaks=0 durationMs={}", gpsTrackId, System.currentTimeMillis() - started);
            return;
        }
        List<GpsTrackEvent> events = stopRanges.stream()
                .map(range -> toDetectedStopEvent(gpsTrackId, gpsTrackDataId, range))
                .toList();
        gpsTrackEventRepository.saveAll(events);
        log.info("Detected break persistence completed: trackId={} breaks={} durationMs={}",
                gpsTrackId, events.size(), System.currentTimeMillis() - started);
    }

    /**
     * Persists source recording boundaries on canonical points for subsequent segment calculations.
     */
    @Transactional
    public void replaceImportedRecordingGaps(Long trackId, Long dataId, List<GpsTrackDataPoint> points,
                                             List<Integer> breakBeforeIndexes) {
        long started = System.currentTimeMillis();
        log.info("Recording gap persistence starting: trackId={} points={}", trackId, points.size());
        gpsTrackEventRepository.deleteByGpsTrackIdAndEventTypeAndSource(
                trackId, GpsTrackEvent.EVENT_TYPE.GPS_GAP, GpsTrackEvent.SOURCE.IMPORTED);
        var breaks = new TreeSet<>(breakBeforeIndexes);
        List<GpsTrackEvent> events = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            GpsTrackDataPoint from = points.get(i - 1);
            GpsTrackDataPoint to = points.get(i);
            Integer nextBreak = breaks.higher(from.getPointIndex());
            if (nextBreak == null || nextBreak > to.getPointIndex()) continue;
            events.add(GpsTrackEvent.builder().gpsTrackId(trackId).gpsTrackDataId(dataId)
                    .eventType(GpsTrackEvent.EVENT_TYPE.GPS_GAP).source(GpsTrackEvent.SOURCE.IMPORTED)
                    .startGpsTrackDataPointId(from.getId()).endGpsTrackDataPointId(to.getId())
                    .startPointIndex(from.getPointIndex()).endPointIndex(to.getPointIndex())
                    .startTimestamp(from.getPointTimestamp()).endTimestamp(to.getPointTimestamp())
                    .durationInSec(recordingGapDuration(from, to))
                    .startDistanceInMeter(from.getDistanceInMeterSinceStart())
                    .endDistanceInMeter(to.getDistanceInMeterSinceStart())
                    .startPointLongLat(from.getPointLongLat()).endPointLongLat(to.getPointLongLat())
                    .label("Recording gap").build());
        }
        gpsTrackEventRepository.saveAll(events);
        log.info("Recording gap persistence completed: trackId={} gaps={} durationMs={}",
                trackId, events.size(), System.currentTimeMillis() - started);
    }

    private static Double recordingGapDuration(GpsTrackDataPoint from, GpsTrackDataPoint to) {
        if (from.getPointTimestamp() == null || to.getPointTimestamp() == null) return null;
        long durationMillis = to.getPointTimestamp().getTime() - from.getPointTimestamp().getTime();
        return durationMillis > 0 ? durationMillis / MILLIS_PER_SECOND : null;
    }

    private static GpsTrackEvent toDetectedStopEvent(Long gpsTrackId,
                                                     Long gpsTrackDataId,
                                                     TrackStopDetector.PointStopRange range) {
        GpsTrackDataPoint startPoint = range.startPoint();
        GpsTrackDataPoint endPoint = range.endPoint();

        return GpsTrackEvent.builder()
                .gpsTrackId(gpsTrackId)
                .gpsTrackDataId(gpsTrackDataId)
                .eventType(GpsTrackEvent.EVENT_TYPE.STOP)
                .source(GpsTrackEvent.SOURCE.DETECTED)
                .startGpsTrackDataPointId(startPoint.getId())
                .endGpsTrackDataPointId(endPoint.getId())
                .startPointIndex(startPoint.getPointIndex())
                .endPointIndex(endPoint.getPointIndex())
                .startTimestamp(startPoint.getPointTimestamp())
                .endTimestamp(endPoint.getPointTimestamp())
                .startDistanceInMeter(startPoint.getDistanceInMeterSinceStart())
                .endDistanceInMeter(endPoint.getDistanceInMeterSinceStart())
                .durationInSec(range.durationInSec())
                .startPointLongLat(startPoint.getPointLongLat())
                .endPointLongLat(endPoint.getPointLongLat())
                .label(range.stopRange().category().name())
                .description(stopDescription(range.stopRange(), startPoint, endPoint))
                .confidence(null)
                .detectionEvidence(range.stopRange().evidence())
                .detectionVersion(TrackMotionAnalyzer.VERSION)
                .startBoundary(range.stopRange().startBoundary())
                .endBoundary(range.stopRange().endBoundary())
                .confirmedAtTimestamp(range.stopRange().confirmedAtS() == null ? null
                        : new Date(Math.round(range.stopRange().confirmedAtS() * MILLIS_PER_SECOND)))
                .createDate(new Date())
                .build();
    }

    private static String stopDescription(TrackStopDetector.StopRange range,
                                          GpsTrackDataPoint startPoint,
                                          GpsTrackDataPoint endPoint) {
        if (range.evidence() == TrackStopDetector.StopEvidence.OBSERVED_STATIONARY) {
            return "Stationary observations; boundaries estimated from adjacent recorded samples.";
        }
        if (range.evidence() == TrackStopDetector.StopEvidence.RECORDING_GAP) {
            double distanceM = distanceBetween(startPoint, endPoint);
            double impliedSpeedKmh = distanceM / Math.max(range.durationInSec(), 1.0)
                                     * MetricConstants.SECONDS_PER_HOUR / MetricConstants.METERS_PER_KILOMETER;
            return String.format(Locale.ROOT,
                    "Low-displacement GPS recording gap: distance=%.1fm, impliedSpeed=%.2f km/h",
                    distanceM,
                    impliedSpeedKmh);
        }
        return String.format(Locale.ROOT,
                "Dense GPS stop cluster: r80=%.1fm, r90=%.1fm, inliers=%.0f%%, rawPoints=%d, deletedPoints=%d",
                range.radiusR80M(),
                range.radiusR90M(),
                range.inlierRatio() * 100.0,
                range.rawPoints(),
                range.deletedPoints());
    }

    private static double distanceBetween(GpsTrackDataPoint startPoint, GpsTrackDataPoint endPoint) {
        if (startPoint == null || endPoint == null
            || startPoint.getPointLongLat() == null || endPoint.getPointLongLat() == null) {
            return 0.0;
        }
        return GPXReader.getDistanceBetweenTwoWGS84(
                startPoint.getPointLongLat().getCoordinate(),
                endPoint.getPointLongLat().getCoordinate());
    }
}
