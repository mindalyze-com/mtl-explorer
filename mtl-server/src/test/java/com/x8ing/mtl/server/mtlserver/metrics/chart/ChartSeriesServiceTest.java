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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChartSeriesServiceTest {

    @Test
    void pendingActivityCalculationHidesOldEnergyButPreservesOtherChartsAndStoredPoints() {
        var tracks = mock(GpsTrackRepository.class);
        var repository = mock(GpsTrackDataPointRepository.class);
        var track = new GpsTrack();
        track.setMetabolicEnergy(new com.x8ing.mtl.server.mtlserver.energy.MetabolicEnergy());
        when(tracks.findById(42L)).thenReturn(Optional.of(track));
        var points = densePoints();
        for (var point : points) {
            point.setPowerWatts(100.0);
            point.setEnergyCumulativeWh(10.0);
            point.setPointAltitude(100.0);
        }
        when(repository.getTrackDetailsByGpsTrackIdAndType(42L, GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name())).thenReturn(points);
        var service = new ChartSeriesService(tracks, repository);
        var response = service.build(42L, new ChartSeriesRequest(XMode.TIME, 20, null, null, null, null));
        assertThat(response.availableMetrics()).contains(MetricKey.ALTITUDE_M).doesNotContain(MetricKey.POWER_WATTS, MetricKey.POWER_WINDOW_WATTS, MetricKey.ENERGY_CUMULATIVE_WH);
        assertThat(points.getLast().getPowerWatts()).isEqualTo(100);
        track.setEnergyNetTotalWh(10.0);
        assertThat(service.build(42L, new ChartSeriesRequest(XMode.TIME, 20, null, null, null, null)).availableMetrics()).contains(MetricKey.ENERGY_CUMULATIVE_WH);
    }

    @Test
    void explicitlyAssessedBadTimingKeepsGeometryAndSuppressesTimeMetricsWithoutChangingStoredPoints() {
        var tracks = mock(GpsTrackRepository.class);
        var repository = mock(GpsTrackDataPointRepository.class);
        var track = new GpsTrack();
        track.setTimingUnreliable(true);
        when(tracks.findById(42L)).thenReturn(Optional.of(track));
        var first = point(0.0, "2032-02-18T08:00:00Z");
        first.setDistanceInMeterSinceStart(0.0);
        first.setPointAltitude(100.0);
        var last = point(30 * 86400.0, "2032-03-19T08:00:00Z");
        last.setDistanceInMeterSinceStart(100.0);
        last.setPointAltitude(120.0);
        last.setPowerWatts(500.0);
        last.setEnergyCumulativeWh(1000.0);
        last.setSpeedInKmhMovingWindow(300.0);
        when(repository.getTrackDetailsByGpsTrackIdAndType(42L, GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name()))
                .thenReturn(List.of(first, last));
        var response = new ChartSeriesService(tracks, repository).build(42L,
                new ChartSeriesRequest(XMode.TIME, 10, null, null, null, null));
        assertThat(response.xMode()).isEqualTo(XMode.DISTANCE);
        assertThat(response.xEnd()).isEqualTo(100.0);
        assertThat(response.availableMetrics()).contains(MetricKey.ALTITUDE_M, MetricKey.DISTANCE_M)
                .doesNotContain(MetricKey.DURATION_S, MetricKey.SPEED_MOVING_WINDOW_KMH, MetricKey.SPEED_WINDOW_KMH,
                        MetricKey.SPEED_BUCKET_AVG_KMH, MetricKey.POWER_WATTS, MetricKey.ENERGY_CUMULATIVE_WH);
        assertThat(response.startTimestamp()).isNull();
        assertThat(last.getDurationSinceStart()).isEqualTo(30 * 86400.0);
        assertThat(last.getPowerWatts()).isEqualTo(500.0);
    }

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
        MetricKey recommendation = ChartSeriesMetrics.recommendSpeedMetric(
                buckets(10, 6, 10),
                List.of(MetricKey.SPEED_WINDOW_KMH, MetricKey.SPEED_BUCKET_AVG_KMH));

        assertThat(recommendation).isEqualTo(MetricKey.SPEED_WINDOW_KMH);
    }

    @Test
    void speedRecommendationSwitchesWhenBucketAverageIsExactlyMarginBetter() {
        MetricKey recommendation = ChartSeriesMetrics.recommendSpeedMetric(
                buckets(20, 4, 7),
                List.of(MetricKey.SPEED_WINDOW_KMH, MetricKey.SPEED_BUCKET_AVG_KMH));

        assertThat(recommendation).isEqualTo(MetricKey.SPEED_BUCKET_AVG_KMH);
    }

    @Test
    void speedRecommendationKeepsWindowWhenBucketAverageIsUnderMargin() {
        MetricKey recommendation = ChartSeriesMetrics.recommendSpeedMetric(
                buckets(20, 4, 6),
                List.of(MetricKey.SPEED_WINDOW_KMH, MetricKey.SPEED_BUCKET_AVG_KMH));

        assertThat(recommendation).isEqualTo(MetricKey.SPEED_WINDOW_KMH);
    }

    @Test
    void speedRecommendationHonorsEmptyBucketWindowAvailability() {
        MetricKey recommendation = ChartSeriesMetrics.recommendSpeedMetric(
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
