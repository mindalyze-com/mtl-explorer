package com.x8ing.mtl.server.mtlserver.metrics.chart;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.MetricKey;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode;

import java.util.List;

/**
 * Inputs accepted by {@link ChartSeriesService#build(long, ChartSeriesRequest)}.
 * <p>
 * All fields are nullable so the service can pick deterministic defaults from
 * the track's activity type and the canonical metric set.
 */
@JsonPropertyOrder({
        "xMode",
        "maxBuckets",
        "windowSec",
        "fromX",
        "toX",
        "metrics"
})
public record ChartSeriesRequest(XMode xMode,
                                 Integer maxBuckets,
                                 Double windowSec,
                                 Double fromX,
                                 Double toX,
                                 List<MetricKey> metrics) {
}
