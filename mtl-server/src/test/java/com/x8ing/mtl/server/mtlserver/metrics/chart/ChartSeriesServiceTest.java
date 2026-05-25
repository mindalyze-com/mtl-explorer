package com.x8ing.mtl.server.mtlserver.metrics.chart;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChartSeriesServiceTest {

    @Test
    void includesTopLevelVisibleDomainTimestampsAtSecondPrecision() {
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        GpsTrackDataPointRepository pointRepository = mock(GpsTrackDataPointRepository.class);
        ChartSeriesService service = new ChartSeriesService(gpsTrackRepository, pointRepository);

        long trackId = 42L;
        when(gpsTrackRepository.findById(trackId)).thenReturn(Optional.of(new GpsTrack()));
        when(pointRepository.getTrackDetailsByGpsTrackIdAndType(
                trackId,
                GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name()))
                .thenReturn(List.of(
                        point(0.0, "2026-05-18T06:10:11.987Z"),
                        point(10.0, "2026-05-18T06:10:21.123Z"),
                        point(20.0, "2026-05-18T06:10:31.999Z")));

        ChartSeriesResponse response = service.build(
                trackId,
                new ChartSeriesRequest(XMode.TIME, 1, null, null, null, null));

        assertThat(response.startTimestamp()).isEqualTo(Instant.parse("2026-05-18T06:10:11Z"));
        assertThat(response.endTimestamp()).isEqualTo(Instant.parse("2026-05-18T06:10:31Z"));
    }

    private static GpsTrackDataPoint point(double durationSinceStart, String timestamp) {
        GpsTrackDataPoint p = new GpsTrackDataPoint();
        p.setDurationSinceStart(durationSinceStart);
        p.setDistanceInMeterSinceStart(durationSinceStart * 10.0);
        p.setDurationBetweenPointsInSec(1.0);
        p.setDistanceInMeterBetweenPoints(10.0);
        p.setPointTimestamp(Date.from(Instant.parse(timestamp)));
        return p;
    }
}
