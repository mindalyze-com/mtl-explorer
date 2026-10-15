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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

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

    private static final Set<MetricKey> ENERGY_METRICS = Set.of(MetricKey.ENERGY_CUMULATIVE_WH, MetricKey.POWER_WATTS, MetricKey.POWER_WINDOW_WATTS);

    private final GpsTrackRepository gpsTrackRepository;
    private final GpsTrackDataPointRepository gpsTrackDataPointRepository;

    public ChartSeriesService(GpsTrackRepository gpsTrackRepository,
                              GpsTrackDataPointRepository gpsTrackDataPointRepository) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.gpsTrackDataPointRepository = gpsTrackDataPointRepository;
    }

    public ChartSeriesResponse build(long trackId, ChartSeriesRequest request) {
        GpsTrack track = gpsTrackRepository.findById(trackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found"));

        List<GpsTrackDataPoint> points = gpsTrackDataPointRepository
                .getTrackDetailsByGpsTrackIdAndType(trackId,
                        GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name());
        ChartSeriesMetrics display = ChartSeriesMetrics.compute(track, points, request.windowSec());
        points = display.points();
        double windowSec = display.windowSec();

        ChartBucketBuilder builder = new ChartBucketBuilder();
        ChartBucketBuilder.Config config = new ChartBucketBuilder.Config(
                track.isTimingUnreliable() ? XMode.DISTANCE : request.xMode() != null ? request.xMode() : XMode.TIME,
                request.maxBuckets() != null ? request.maxBuckets() : ChartBucketBuilder.DEFAULT_MAX_BUCKETS,
                track.isTimingUnreliable() && request.xMode() != XMode.DISTANCE ? null : request.fromX(),
                track.isTimingUnreliable() && request.xMode() != XMode.DISTANCE ? null : request.toX(), request.metrics());
        ChartBucketBuilder.Result result = builder.build(points, display.rates(), display.powerWatts(), config);
        List<ChartBucket> buckets = track.getEnergyNetTotalWh() == null && track.getMetabolicEnergy() != null
                ? result.buckets().stream().map(ChartSeriesService::withoutEnergy).toList() : result.buckets();
        List<MetricKey> availableMetrics = metricKeys(buckets);
        MetricKey recommendedSpeedMetric = ChartSeriesMetrics.recommendSpeedMetric(buckets, availableMetrics);

        return new ChartSeriesResponse(
                trackId,
                GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name(),
                result.xMode(),
                windowSec,
                result.xStart(),
                result.xEnd(),
                startTimestamp(buckets),
                endTimestamp(buckets),
                buckets.size(),
                points.size(),
                availableMetrics,
                recommendedSpeedMetric,
                metricDefinitions(availableMetrics),
                buckets);
    }

    private static ChartBucket withoutEnergy(ChartBucket bucket) {
        var metrics = new LinkedHashMap<>(bucket.metrics());
        ENERGY_METRICS.forEach(metrics::remove);
        return new ChartBucket(bucket.index(), bucket.xStart(), bucket.xEnd(), bucket.firstPointIndex(),
                bucket.lastPointIndex(), bucket.representativePointIndex(), bucket.firstTimestamp(),
                bucket.lastTimestamp(), bucket.representativeTimestamp(), metrics);
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

}
