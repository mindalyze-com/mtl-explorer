package com.x8ing.mtl.server.mtlserver.metrics.chart;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.ChartBucket;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.ChartBucketBuilder;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.MetricKey;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode;
import com.x8ing.mtl.server.mtlserver.metrics.window.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * On-the-fly chart-series builder. Loads the canonical
 * {@link GpsTrackData.TRACK_TYPE#RAW_OUTLIER_CLEANED} point stream for the
 * track, runs the trailing-window calculator(s), and folds the result into
 * equal-width chart buckets via {@link ChartBucketBuilder}.
 * <p>
 * Nothing is persisted; the response is fully derived on each call. The class
 * has no awareness of how points were ingested — it consumes the canonical
 * stream only.
 */
@Service
@JsonPropertyOrder({
        "gpsTrackRepository",
        "gpsTrackDataPointRepository"
})
public class ChartSeriesService {

    private final GpsTrackRepository gpsTrackRepository;
    private final GpsTrackDataPointRepository gpsTrackDataPointRepository;

    public ChartSeriesService(GpsTrackRepository gpsTrackRepository,
                              GpsTrackDataPointRepository gpsTrackDataPointRepository) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.gpsTrackDataPointRepository = gpsTrackDataPointRepository;
    }

    public ChartSeriesResponse build(long trackId, ChartSeriesRequest request) {
        GpsTrack track = gpsTrackRepository.findById(trackId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown track: " + trackId));

        List<GpsTrackDataPoint> points = gpsTrackDataPointRepository
                .getTrackDetailsByGpsTrackIdAndType(trackId,
                        GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name());

        double windowSec = request.windowSec() != null && request.windowSec() > 0
                ? request.windowSec()
                : GpsTrackDataPointWindowAdapter.defaultWindowSecFor(track.getActivityType());

        PointWindowedRateCalculator rateCalc = new PointWindowedRateCalculator(windowSec);
        List<WindowedRateSample> rateSamples = rateCalc.compute(points, GpsTrackDataPointWindowAdapter.view());

        Double[] rollingPower = computeRollingPower(points, windowSec);

        ChartBucketBuilder builder = new ChartBucketBuilder();
        ChartBucketBuilder.Config config = new ChartBucketBuilder.Config(
                request.xMode() != null ? request.xMode() : XMode.TIME,
                request.maxBuckets() != null ? request.maxBuckets() : ChartBucketBuilder.DEFAULT_MAX_BUCKETS,
                request.fromX(), request.toX(), request.metrics());
        ChartBucketBuilder.Result result = builder.build(points, rateSamples, rollingPower, config);
        List<MetricKey> availableMetrics = metricKeys(result.buckets());

        return new ChartSeriesResponse(
                trackId,
                GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name(),
                result.xMode(),
                windowSec,
                result.xStart(),
                result.xEnd(),
                startTimestamp(result.buckets()),
                endTimestamp(result.buckets()),
                result.buckets().size(),
                points.size(),
                availableMetrics,
                metricDefinitions(availableMetrics),
                result.buckets());
    }

    private static Instant startTimestamp(List<ChartBucket> buckets) {
        return buckets.isEmpty() ? null : buckets.getFirst().firstTimestamp();
    }

    private static Instant endTimestamp(List<ChartBucket> buckets) {
        return buckets.isEmpty() ? null : buckets.getLast().lastTimestamp();
    }

    private static List<MetricKey> metricKeys(List<ChartBucket> buckets) {
        return buckets.stream()
                .flatMap(b -> b.metrics().keySet().stream())
                .distinct()
                .toList();
    }

    private static List<MetricDefinition> metricDefinitions(List<MetricKey> keys) {
        return keys.stream()
                .map(MetricDefinition::from)
                .toList();
    }

    private static Double[] computeRollingPower(List<GpsTrackDataPoint> points, double windowSec) {
        if (points == null || points.isEmpty()) return new Double[0];
        int n = points.size();
        double[] powers = new double[n];
        double[] durations = new double[n];
        for (int i = 0; i < n; i++) {
            Double p = points.get(i).getPowerWatts();
            Double d = points.get(i).getDurationBetweenPointsInSec();
            powers[i] = p != null ? p : 0;
            durations[i] = d != null ? d : 0;
        }
        PowerWindowStats stats = new PointWindowedPowerCalculator(windowSec).compute(powers, durations);
        return stats.rollingPowerWatts();
    }
}
