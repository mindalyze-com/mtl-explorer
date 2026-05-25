package com.x8ing.mtl.server.mtlserver.logic.motion;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
@JsonPropertyOrder({
        "gpsTrackEventRepository"
})
public class GpsTrackEventService {

    private static final double SECONDS_PER_HOUR = 3600.0;
    private static final double METERS_PER_KILOMETER = 1000.0;

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
        gpsTrackEventRepository.deleteByGpsTrackIdAndEventTypeAndSource(
                gpsTrackId,
                GpsTrackEvent.EVENT_TYPE.STOP,
                GpsTrackEvent.SOURCE.DETECTED);
        if (stopRanges == null || stopRanges.isEmpty()) {
            return;
        }
        List<GpsTrackEvent> events = stopRanges.stream()
                .map(range -> toDetectedStopEvent(gpsTrackId, gpsTrackDataId, range))
                .toList();
        gpsTrackEventRepository.saveAll(events);
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
                .confidence(range.stopRange().inlierRatio())
                .createDate(new Date())
                .build();
    }

    private static String stopDescription(TrackStopDetector.StopRange range,
                                          GpsTrackDataPoint startPoint,
                                          GpsTrackDataPoint endPoint) {
        if (range.evidence() == TrackStopDetector.StopEvidence.RECORDING_GAP) {
            double distanceM = distanceBetween(startPoint, endPoint);
            double impliedSpeedKmh = distanceM / Math.max(range.durationInSec(), 1.0) * SECONDS_PER_HOUR / METERS_PER_KILOMETER;
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
