package com.x8ing.mtl.server.mtlserver.metrics.chart;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.MetricKey;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Read-only endpoint that produces chart-friendly bucketed time/distance
 * series for a track on demand.
 * <p>
 * Per the architecture doc {@code canonical_metric_lod_architecture.md} this
 * endpoint replaces the persisted {@code SIMPLIFIED_FIXED_POINTS} variants —
 * the response is computed from the canonical
 * {@code RAW_OUTLIER_CLEANED} stream on every call.
 */
@RestController
@RequestMapping("/api/tracks")
@JsonPropertyOrder({
        "chartSeriesService"
})
public class ChartSeriesController {

    private static final int CHART_SERIES_CACHE_SECONDS = 300;

    private final ChartSeriesService chartSeriesService;

    public ChartSeriesController(ChartSeriesService chartSeriesService) {
        this.chartSeriesService = chartSeriesService;
    }

    @GetMapping("/{trackId}/chart-series")
    public ResponseEntity<ChartSeriesResponse> getChartSeries(
            @PathVariable long trackId,
            @RequestParam(name = "x", defaultValue = "TIME") XMode xMode,
            @RequestParam(name = "maxBuckets", required = false) Integer maxBuckets,
            @RequestParam(name = "windowSec", required = false) Double windowSec,
            @RequestParam(name = "from", required = false) Double fromX,
            @RequestParam(name = "to", required = false) Double toX,
            @RequestParam(name = "metrics", required = false) List<MetricKey> metrics
    ) {
        ChartSeriesRequest request = new ChartSeriesRequest(xMode, maxBuckets, windowSec, fromX, toX, metrics);
        ChartSeriesResponse response = chartSeriesService.build(trackId, request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CHART_SERIES_CACHE_SECONDS, TimeUnit.SECONDS).cachePrivate().mustRevalidate())
                .body(response);
    }
}
