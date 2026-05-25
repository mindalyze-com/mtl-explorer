package com.x8ing.mtl.server.mtlserver.metrics.bucket;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.metrics.chart.MetricDefinition;
import com.x8ing.mtl.server.mtlserver.metrics.window.WindowedRateSample;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChartBucketBuilderTest {

    @Test
    void roundsMetricStatsWithMetricResponsePrecision() {
        ChartBucketBuilder builder = new ChartBucketBuilder();
        ChartBucketBuilder.Result result = builder.build(
                List.of(
                        point(0.0, 0.0, 100.0, 0.0, 0.0, 0.0, 10.0, 1.0, 50.0, 0.0),
                        point(10.1236, 123.456, 142.35, 9.951, 8.95, 12.345, 3.14, 1.0, 52.6, 1.9948617894804577)),
                List.of(
                        new WindowedRateSample(10.0, 100.0, 200.0),
                        new WindowedRateSample(6.789, 123.56, 223.49)),
                new Double[]{50.0, 53.733333333333334},
                new ChartBucketBuilder.Config(XMode.TIME, 2, null, null, null));

        Map<MetricKey, MetricBucketStats> metrics = result.buckets().get(1).metrics();

        assertThat(metrics.get(MetricKey.ALTITUDE_M).getAvg()).isEqualTo(142.4);
        assertThat(metrics.get(MetricKey.DISTANCE_M).getLast()).isEqualTo(123.5);
        assertThat(metrics.get(MetricKey.DURATION_S).getLast()).isEqualTo(10.124);
        assertThat(metrics.get(MetricKey.ASCENT_M).getLast()).isEqualTo(10.0);
        assertThat(metrics.get(MetricKey.DESCENT_M).getLast()).isEqualTo(9.0);
        assertThat(metrics.get(MetricKey.SPEED_MOVING_WINDOW_KMH).getAvg()).isEqualTo(12.35);
        assertThat(metrics.get(MetricKey.SPEED_WINDOW_KMH).getAvg()).isEqualTo(6.79);
        assertThat(metrics.get(MetricKey.ELEVATION_GAIN_PER_HOUR_WINDOW).getAvg()).isEqualTo(124.0);
        assertThat(metrics.get(MetricKey.ELEVATION_LOSS_PER_HOUR_WINDOW).getAvg()).isEqualTo(223.0);
        assertThat(metrics.get(MetricKey.SLOPE_PERCENT).getAvg()).isEqualTo(3.1);
        assertThat(metrics.get(MetricKey.POWER_WATTS).getAvg()).isEqualTo(53.0);
        assertThat(metrics.get(MetricKey.POWER_WINDOW_WATTS).getAvg()).isEqualTo(54.0);
        assertThat(metrics.get(MetricKey.ENERGY_CUMULATIVE_WH).getLast()).isEqualTo(1.995);
    }

    @Test
    void includesUtcSecondTimestampsForBucketBoundaryPoints() {
        GpsTrackDataPoint first = point(0.0, 0.0, 100.0, 0.0, 0.0, 0.0, 0.0, 1.0, 50.0, 0.0);
        first.setPointTimestamp(Date.from(Instant.parse("2026-05-18T06:10:11.987Z")));
        GpsTrackDataPoint middle = point(10.0, 100.0, 101.0, 1.0, 0.0, 1.0, 1.0, 1.0, 51.0, 1.0);
        middle.setPointTimestamp(Date.from(Instant.parse("2026-05-18T06:10:21.123Z")));
        GpsTrackDataPoint last = point(20.0, 200.0, 102.0, 2.0, 0.0, 2.0, 2.0, 1.0, 52.0, 2.0);
        last.setPointTimestamp(Date.from(Instant.parse("2026-05-18T06:10:31.999Z")));

        ChartBucketBuilder.Result result = new ChartBucketBuilder().build(
                List.of(first, middle, last),
                List.of(WindowedRateSample.empty(), WindowedRateSample.empty(), WindowedRateSample.empty()),
                null,
                new ChartBucketBuilder.Config(XMode.TIME, 1, null, null, null));

        ChartBucket bucket = result.buckets().getFirst();

        assertThat(bucket.firstTimestamp()).isEqualTo(Instant.parse("2026-05-18T06:10:11Z"));
        assertThat(bucket.lastTimestamp()).isEqualTo(Instant.parse("2026-05-18T06:10:31Z"));
        assertThat(bucket.representativeTimestamp()).isEqualTo(Instant.parse("2026-05-18T06:10:21Z"));
    }

    @Test
    void metricDefinitionUsesMetricKeyMetadata() {
        MetricDefinition definition = MetricDefinition.from(MetricKey.POWER_WINDOW_WATTS);

        assertThat(definition.key()).isEqualTo(MetricKey.POWER_WINDOW_WATTS);
        assertThat(definition.description()).isEqualTo("Trailing-window average power");
        assertThat(definition.unit()).isEqualTo("W");
        assertThat(definition.responseFractionDigits()).isZero();
    }

    @Test
    void roundsTimeDomainValuesToThreeFractionDigits() {
        ChartBucketBuilder.Result result = new ChartBucketBuilder().build(
                List.of(
                        point(0.0, 0.0, 100.0, 0.0, 0.0, 0.0, 0.0, 1.0, 50.0, 0.0),
                        point(10.123456, 123.456, 101.0, 0.0, 0.0, 0.0, 0.0, 1.0, 51.0, 0.0)),
                null,
                null,
                new ChartBucketBuilder.Config(XMode.TIME, 2, null, null, null));

        assertThat(result.xStart()).isEqualTo(0.0);
        assertThat(result.xEnd()).isEqualTo(10.123);
        assertThat(result.buckets().getFirst().xStart()).isEqualTo(0.0);
        assertThat(result.buckets().getFirst().xEnd()).isEqualTo(5.062);
        assertThat(result.buckets().getLast().xStart()).isEqualTo(5.062);
        assertThat(result.buckets().getLast().xEnd()).isEqualTo(10.123);
    }

    @Test
    void roundsDistanceDomainValuesToOneFractionDigit() {
        ChartBucketBuilder.Result result = new ChartBucketBuilder().build(
                List.of(
                        point(0.0, 0.0, 100.0, 0.0, 0.0, 0.0, 0.0, 1.0, 50.0, 0.0),
                        point(10.0, 123.456, 101.0, 0.0, 0.0, 0.0, 0.0, 1.0, 51.0, 0.0)),
                null,
                null,
                new ChartBucketBuilder.Config(XMode.DISTANCE, 2, null, null, null));

        assertThat(result.xStart()).isEqualTo(0.0);
        assertThat(result.xEnd()).isEqualTo(123.5);
        assertThat(result.buckets().getFirst().xStart()).isEqualTo(0.0);
        assertThat(result.buckets().getFirst().xEnd()).isEqualTo(61.7);
        assertThat(result.buckets().getLast().xStart()).isEqualTo(61.7);
        assertThat(result.buckets().getLast().xEnd()).isEqualTo(123.5);
    }

    private static GpsTrackDataPoint point(double durationSinceStart,
                                           double distanceSinceStart,
                                           double altitude,
                                           double ascentSinceStart,
                                           double descentSinceStart,
                                           double speedMovingWindow,
                                           double slopePercent,
                                           double durationBetweenPoints,
                                           double powerWatts,
                                           double energyCumulativeWh) {
        GpsTrackDataPoint point = new GpsTrackDataPoint();
        point.setDurationSinceStart(durationSinceStart);
        point.setDistanceInMeterSinceStart(distanceSinceStart);
        point.setPointAltitude(altitude);
        point.setAscentInMeterSinceStart(ascentSinceStart);
        point.setDescentInMeterSinceStart(descentSinceStart);
        point.setSpeedInKmhMovingWindow(speedMovingWindow);
        point.setSlopePercentageInMovingWindow(slopePercent);
        point.setDurationBetweenPointsInSec(durationBetweenPoints);
        point.setPowerWatts(powerWatts);
        point.setEnergyCumulativeWh(energyCumulativeWh);
        return point;
    }
}
