package com.x8ing.mtl.server.mtlserver.logic.motion;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class GpsTrackEventService {

    private static final double DETECTED_STOP_CONFIDENCE = 1.0;

    private final GpsTrackEventRepository gpsTrackEventRepository;

    public GpsTrackEventService(GpsTrackEventRepository gpsTrackEventRepository) {
        this.gpsTrackEventRepository = gpsTrackEventRepository;
    }

    /**
     * Replaces the generated stop events for a track while leaving future
     * user-created/manual events intact.
     */
    @Transactional
    public void replaceDetectedStopEvents(Long gpsTrackId, Long gpsTrackDataId, List<TrackMotionAnalyzer.StopRange> stopRanges) {
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
                                                     TrackMotionAnalyzer.StopRange range) {
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
                .confidence(DETECTED_STOP_CONFIDENCE)
                .createDate(new Date())
                .build();
    }
}
