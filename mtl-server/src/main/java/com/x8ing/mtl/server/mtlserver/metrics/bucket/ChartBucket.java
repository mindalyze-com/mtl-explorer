package com.x8ing.mtl.server.mtlserver.metrics.bucket;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.Map;

/**
 * One bucket in a chart-series response.
 * <p>
 * Each bucket spans the half-open x-range {@code [xStart, xEnd)} along the
 * track. The {@code firstPointIndex} / {@code lastPointIndex} pair gives the
 * canonical point indices included in the bucket (inclusive on both ends).
 * {@code representativePointIndex} is the midpoint suggested for cursor-sync
 * when the user hovers the bucket in the chart. The timestamp fields are UTC
 * ISO-8601 instants truncated to second precision.
 * <p>
 * Per-metric statistics live in {@link #metrics} and are keyed by
 * {@link MetricKey} JSON names; absent keys mean the bucket had no usable data
 * for that metric.
 * <p>
 * Implemented as a record so Jackson + Springdoc serialize component names
 * verbatim ({@code xStart}/{@code xEnd}), matching the rest of the API
 * surface; a class with {@code getXStart()} getters previously serialized as
 * lowercase {@code xstart}/{@code xend} due to JavaBeans introspection.
 */
@JsonPropertyOrder({
        "index",
        "xStart",
        "xEnd",
        "firstPointIndex",
        "lastPointIndex",
        "representativePointIndex",
        "firstTimestamp",
        "lastTimestamp",
        "representativeTimestamp",
        "metrics"
})
public record ChartBucket(
        int index,
        double xStart,
        double xEnd,
        int firstPointIndex,
        int lastPointIndex,
        int representativePointIndex,
        Instant firstTimestamp,
        Instant lastTimestamp,
        Instant representativeTimestamp,
        Map<MetricKey, MetricBucketStats> metrics) {
}
