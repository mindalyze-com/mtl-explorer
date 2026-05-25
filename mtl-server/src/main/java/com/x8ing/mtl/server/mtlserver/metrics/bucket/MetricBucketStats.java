package com.x8ing.mtl.server.mtlserver.metrics.bucket;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Per-bucket statistics for a single numeric metric.
 * <p>
 * Provides everything the frontend graph layer needs:
 * <ul>
 *   <li>{@code avg} — time-weighted mean over the bucket (NaN-safe, null if
 *       no point in the bucket carried a value).</li>
 *   <li>{@code min}, {@code max} — extrema with the canonical point indices
 *       where they occurred ({@code minPointIndex}, {@code maxPointIndex}),
 *       enabling cursor-sync between graph and map.</li>
 *   <li>{@code first}, {@code last} — values at the first and last canonical
 *       points falling in the bucket — used by cumulative series (delta =
 *       last - first).</li>
 * </ul>
 * Null values mean "no data" rather than zero.
 */
@JsonPropertyOrder({
        "avg",
        "min",
        "max",
        "first",
        "last",
        "minPointIndex",
        "maxPointIndex",
        "sampleCount"
})
public final class MetricBucketStats {

    private final Double avg;
    private final Double min;
    private final Double max;
    private final Double first;
    private final Double last;
    private final Integer minPointIndex;
    private final Integer maxPointIndex;
    private final int sampleCount;

    public MetricBucketStats(Double avg, Double min, Double max, Double first, Double last,
                             Integer minPointIndex, Integer maxPointIndex, int sampleCount) {
        this.avg = avg;
        this.min = min;
        this.max = max;
        this.first = first;
        this.last = last;
        this.minPointIndex = minPointIndex;
        this.maxPointIndex = maxPointIndex;
        this.sampleCount = sampleCount;
    }

    public Double getAvg() {
        return avg;
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }

    public Double getFirst() {
        return first;
    }

    public Double getLast() {
        return last;
    }

    public Integer getMinPointIndex() {
        return minPointIndex;
    }

    public Integer getMaxPointIndex() {
        return maxPointIndex;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public static MetricBucketStats empty() {
        return new MetricBucketStats(null, null, null, null, null, null, null, 0);
    }
}
