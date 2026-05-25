package com.x8ing.mtl.server.mtlserver.metrics.chart;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.ChartBucket;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.MetricKey;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode;

import java.time.Instant;
import java.util.List;

/**
 * Response payload for the {@code /api/tracks/{id}/chart-series} endpoint.
 * <p>
 * The response is derived on each call from the canonical
 * {@code RAW_OUTLIER_CLEANED} point stream — nothing here is persisted on the
 * server.
 *
 * @param trackId             the source track id
 * @param sourceTrackType     canonical variant the buckets were aggregated
 *                            over (always {@code RAW_OUTLIER_CLEANED}
 *                            today, recorded for forward-compat)
 * @param xMode               axis selector used to lay out buckets
 * @param windowSec           trailing-window length (s) used for the
 *                            on-the-fly window metrics
 * @param xStart              inclusive start of the visible x-domain
 * @param xEnd                inclusive end of the visible x-domain
 * @param startTimestamp      UTC timestamp of the first visible bucket,
 *                            truncated to second precision
 * @param endTimestamp        UTC timestamp of the last visible bucket,
 *                            truncated to second precision
 * @param bucketCount         number of populated buckets
 * @param canonicalPointCount total canonical points read for this response
 * @param availableMetrics    union of metric keys actually present in at
 *                            least one bucket — convenience for the client
 *                            so it can decide which graphs to render
 * @param metricDefinitions   metadata for each available metric key,
 *                            including description, unit, and response
 *                            precision
 * @param buckets             the bucket series, ordered by index
 */
@JsonPropertyOrder({
        "trackId",
        "sourceTrackType",
        "xMode",
        "windowSec",
        "xStart",
        "xEnd",
        "startTimestamp",
        "endTimestamp",
        "bucketCount",
        "canonicalPointCount",
        "availableMetrics",
        "metricDefinitions",
        "buckets"
})
public record ChartSeriesResponse(long trackId,
                                  String sourceTrackType,
                                  XMode xMode,
                                  double windowSec,
                                  double xStart,
                                  double xEnd,
                                  Instant startTimestamp,
                                  Instant endTimestamp,
                                  int bucketCount,
                                  int canonicalPointCount,
                                  List<MetricKey> availableMetrics,
                                  List<MetricDefinition> metricDefinitions,
                                  List<ChartBucket> buckets) {
}
