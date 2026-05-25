package com.x8ing.mtl.server.mtlserver.logic.motion;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.util.Date;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GpsTrackEventServiceTest {

    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Test
    @SuppressWarnings("unchecked")
    void replaceDetectedStopEventsStoresEventLocationsAtCreationTime() {
        GpsTrackEventRepository gpsTrackEventRepository = mock(GpsTrackEventRepository.class);
        GpsTrackEventService service = new GpsTrackEventService(gpsTrackEventRepository);
        Point startLocation = point(8.501, 47.401);
        Point endLocation = point(8.502, 47.402);

        service.replaceDetectedStopEvents(101L, 202L, List.of(new TrackStopDetector.PointStopRange(
                dataPoint(303L, 10, startLocation, 100.0),
                dataPoint(404L, 20, endLocation, 250.0),
                new TrackStopDetector.StopRange(
                        10,
                        20,
                        100.0,
                        250.0,
                        8.5015,
                        47.4015,
                        420.0,
                        TrackStopDetector.StopCategory.BREAK,
                        3.5,
                        4.5,
                        0.95,
                        30,
                        8))));

        verify(gpsTrackEventRepository).saveAll(argThat(events -> {
            GpsTrackEvent event = StreamSupport.stream(events.spliterator(), false).findFirst().orElseThrow();
            assertThat(event.getStartPointLongLat()).isSameAs(startLocation);
            assertThat(event.getEndPointLongLat()).isSameAs(endLocation);
            assertThat(event.getStartGpsTrackDataPointId()).isEqualTo(303L);
            assertThat(event.getEndGpsTrackDataPointId()).isEqualTo(404L);
            assertThat(event.getStartPointIndex()).isEqualTo(10);
            assertThat(event.getEndPointIndex()).isEqualTo(20);
            return true;
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceDetectedStopEventsDescribesRecordingGaps() {
        GpsTrackEventRepository gpsTrackEventRepository = mock(GpsTrackEventRepository.class);
        GpsTrackEventService service = new GpsTrackEventService(gpsTrackEventRepository);
        Point startLocation = point(8.501, 47.401);
        Point endLocation = point(8.5012, 47.401);

        service.replaceDetectedStopEvents(101L, 202L, List.of(new TrackStopDetector.PointStopRange(
                dataPoint(303L, 10, startLocation, 100.0),
                dataPoint(404L, 20, endLocation, 250.0),
                new TrackStopDetector.StopRange(
                        10,
                        20,
                        100.0,
                        4_210.0,
                        8.5011,
                        47.401,
                        420.0,
                        TrackStopDetector.StopCategory.LONG_BREAK,
                        7.5,
                        7.5,
                        1.0,
                        2,
                        0,
                        TrackStopDetector.StopEvidence.RECORDING_GAP))));

        verify(gpsTrackEventRepository).saveAll(argThat(events -> {
            GpsTrackEvent event = StreamSupport.stream(events.spliterator(), false).findFirst().orElseThrow();
            assertThat(event.getLabel()).isEqualTo("LONG_BREAK");
            assertThat(event.getDescription()).startsWith("Low-displacement GPS recording gap:");
            assertThat(event.getDescription()).contains("impliedSpeed=");
            return true;
        }));
    }

    private GpsTrackDataPoint dataPoint(Long id, Integer pointIndex, Point pointLongLat, Double distanceInMeterSinceStart) {
        GpsTrackDataPoint dataPoint = new GpsTrackDataPoint();
        dataPoint.setId(id);
        dataPoint.setPointIndex(pointIndex);
        dataPoint.setPointTimestamp(new Date(1_000L + pointIndex));
        dataPoint.setPointLongLat(pointLongLat);
        dataPoint.setDistanceInMeterSinceStart(distanceInMeterSinceStart);
        return dataPoint;
    }

    private Point point(double lng, double lat) {
        return geometryFactory.createPoint(new Coordinate(lng, lat));
    }
}
