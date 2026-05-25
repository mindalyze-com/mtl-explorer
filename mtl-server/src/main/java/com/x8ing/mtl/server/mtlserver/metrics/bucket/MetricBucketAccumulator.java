package com.x8ing.mtl.server.mtlserver.metrics.bucket;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Mutable accumulator for a single metric inside a single bucket. Folded into
 * an immutable {@link MetricBucketStats} at the end of bucket assembly.
 */
@JsonPropertyOrder({
        "sumWeighted",
        "sumWeight",
        "min",
        "max",
        "minPointIndex",
        "maxPointIndex",
        "first",
        "last",
        "sampleCount"
})
final class MetricBucketAccumulator {

    private double sumWeighted;
    private double sumWeight;
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;
    private Integer minPointIndex;
    private Integer maxPointIndex;
    private Double first;
    private Double last;
    private int sampleCount;

    /**
     * Accept one observation for the metric in this bucket.
     *
     * @param value      observation value (NaN/infinite/null → ignored).
     * @param weight     weighting for the time-weighted mean (typically the
     *                   segment duration in seconds; pass 1.0 for an
     *                   unweighted mean).
     * @param pointIndex canonical point index that produced the value;
     *                   captured for the min / max attribution and to allow
     *                   cursor-sync from chart back to the map.
     */
    void add(Double value, double weight, int pointIndex) {
        if (value == null || !Double.isFinite(value)) return;
        if (first == null) first = value;
        last = value;
        sampleCount++;
        if (Double.isFinite(weight) && weight > 0) {
            sumWeighted += value * weight;
            sumWeight += weight;
        }
        if (value < min) {
            min = value;
            minPointIndex = pointIndex;
        }
        if (value > max) {
            max = value;
            maxPointIndex = pointIndex;
        }
    }

    MetricBucketStats finish() {
        if (sampleCount == 0) return MetricBucketStats.empty();
        Double avg = sumWeight > 0 ? sumWeighted / sumWeight : null;
        Double minV = Double.isFinite(min) ? min : null;
        Double maxV = Double.isFinite(max) ? max : null;
        return new MetricBucketStats(avg, minV, maxV, first, last, minPointIndex, maxPointIndex, sampleCount);
    }
}
