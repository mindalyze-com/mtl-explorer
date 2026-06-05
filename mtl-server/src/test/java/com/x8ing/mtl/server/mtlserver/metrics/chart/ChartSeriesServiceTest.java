package com.x8ing.mtl.server.mtlserver.metrics.chart;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.ChartBucket;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.MetricBucketStats;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.MetricKey;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void speedRecommendationKeepsWindowAtGoodCoverageBoundary() {
        MetricKey recommendation = ChartSeriesService.recommendSpeedMetric(
                buckets(10, 6, 10),
                List.of(MetricKey.SPEED_WINDOW_KMH, MetricKey.SPEED_BUCKET_AVG_KMH));

        assertThat(recommendation).isEqualTo(MetricKey.SPEED_WINDOW_KMH);
    }

    @Test
    void speedRecommendationSwitchesWhenBucketAverageIsExactlyMarginBetter() {
        MetricKey recommendation = ChartSeriesService.recommendSpeedMetric(
                buckets(20, 4, 7),
                List.of(MetricKey.SPEED_WINDOW_KMH, MetricKey.SPEED_BUCKET_AVG_KMH));

        assertThat(recommendation).isEqualTo(MetricKey.SPEED_BUCKET_AVG_KMH);
    }

    @Test
    void speedRecommendationKeepsWindowWhenBucketAverageIsUnderMargin() {
        MetricKey recommendation = ChartSeriesService.recommendSpeedMetric(
                buckets(20, 4, 6),
                List.of(MetricKey.SPEED_WINDOW_KMH, MetricKey.SPEED_BUCKET_AVG_KMH));

        assertThat(recommendation).isEqualTo(MetricKey.SPEED_WINDOW_KMH);
    }

    @Test
    void speedRecommendationHonorsEmptyBucketWindowAvailability() {
        MetricKey recommendation = ChartSeriesService.recommendSpeedMetric(
                List.of(),
                List.of(MetricKey.SPEED_WINDOW_KMH));

        assertThat(recommendation).isEqualTo(MetricKey.SPEED_WINDOW_KMH);
    }

    @Test
    void sparseTrackWithFallbackFilteredOutDoesNotRecommendFallback() {
        ChartSeriesResponse response = serviceWithPoints(sparsePoints()).build(
                42L,
                new ChartSeriesRequest(
                        XMode.TIME,
                        20,
                        null,
                        null,
                        null,
                        List.of(MetricKey.SPEED_WINDOW_KMH)));

        assertThat(response.recommendedSpeedMetric()).isNull();
        assertThat(response.availableMetrics()).doesNotContain(MetricKey.SPEED_BUCKET_AVG_KMH);
    }

    @Test
    void filterExcludingBothSpeedMetricsProducesNoSpeedRecommendation() {
        ChartSeriesResponse response = serviceWithPoints(densePoints()).build(
                42L,
                new ChartSeriesRequest(
                        XMode.TIME,
                        1,
                        null,
                        null,
                        null,
                        List.of(MetricKey.ALTITUDE_M)));

        assertThat(response.recommendedSpeedMetric()).isNull();
        assertThat(response.availableMetrics()).containsExactly(MetricKey.ALTITUDE_M);
    }

    @Test
    void denseSyntheticTrackRecommendsWindowSpeed() {
        ChartSeriesResponse response = serviceWithPoints(densePoints()).build(
                42L,
                new ChartSeriesRequest(XMode.TIME, 1, null, null, null, null));

        assertThat(response.recommendedSpeedMetric()).isEqualTo(MetricKey.SPEED_WINDOW_KMH);
        assertThat(response.availableMetrics()).contains(MetricKey.SPEED_WINDOW_KMH);
    }

    @Test
    void sparseSyntheticTrackRecommendsBucketAverageSpeed() {
        ChartSeriesResponse response = serviceWithPoints(sparsePoints()).build(
                42L,
                new ChartSeriesRequest(XMode.TIME, 20, null, null, null, null));

        assertThat(response.recommendedSpeedMetric()).isEqualTo(MetricKey.SPEED_BUCKET_AVG_KMH);
        assertThat(response.availableMetrics()).contains(MetricKey.SPEED_BUCKET_AVG_KMH);
    }

    private static ChartSeriesService serviceWithPoints(List<GpsTrackDataPoint> points) {
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        GpsTrackDataPointRepository pointRepository = mock(GpsTrackDataPointRepository.class);
        long trackId = 42L;
        when(gpsTrackRepository.findById(trackId)).thenReturn(Optional.of(new GpsTrack()));
        when(pointRepository.getTrackDetailsByGpsTrackIdAndType(
                trackId,
                GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name()))
                .thenReturn(points);
        return new ChartSeriesService(gpsTrackRepository, pointRepository);
    }

    private static List<ChartBucket> buckets(int count, int windowSpeedCount, int bucketAverageSpeedCount) {
        List<ChartBucket> buckets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<MetricKey, MetricBucketStats> metrics = new LinkedHashMap<>();
            if (i < windowSpeedCount) {
                metrics.put(MetricKey.SPEED_WINDOW_KMH, stats());
            }
            if (i < bucketAverageSpeedCount) {
                metrics.put(MetricKey.SPEED_BUCKET_AVG_KMH, stats());
            }
            buckets.add(new ChartBucket(i, i, i + 1, i, i, i, null, null, null, metrics));
        }
        return buckets;
    }

    private static MetricBucketStats stats() {
        return new MetricBucketStats(1.0, 1.0, 1.0, 1.0, 1.0, 0, 0, 1);
    }

    private static List<GpsTrackDataPoint> densePoints() {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            points.add(point(i, i * 10.0, i == 0 ? 0.0 : 1.0, i == 0 ? 0.0 : 10.0));
        }
        return points;
    }

    private static List<GpsTrackDataPoint> sparsePoints() {
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            points.add(point(i * 45.0, i * 100.0, i == 0 ? 0.0 : 45.0, i == 0 ? 0.0 : 100.0));
        }
        return points;
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

    private static GpsTrackDataPoint point(double durationSinceStart,
                                           double distanceSinceStart,
                                           double durationBetweenPoints,
                                           double distanceBetweenPoints) {
        GpsTrackDataPoint p = new GpsTrackDataPoint();
        p.setDurationSinceStart(durationSinceStart);
        p.setDistanceInMeterSinceStart(distanceSinceStart);
        p.setDurationBetweenPointsInSec(durationBetweenPoints);
        p.setDistanceInMeterBetweenPoints(distanceBetweenPoints);
        p.setPointAltitude(100.0);
        p.setAscentInMeterSinceStart(0.0);
        p.setDescentInMeterSinceStart(0.0);
        return p;
    }
}
