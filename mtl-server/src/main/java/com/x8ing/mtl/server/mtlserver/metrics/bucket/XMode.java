package com.x8ing.mtl.server.mtlserver.metrics.bucket;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * X-axis selector for chart bucket aggregation.
 * <ul>
 *   <li>{@link #TIME} buckets cover equal slices of elapsed time (seconds
 *       since track start).</li>
 *   <li>{@link #DISTANCE} buckets cover equal slices of distance travelled
 *       (metres since track start).</li>
 * </ul>
 */
public enum XMode {
    TIME(3),
    DISTANCE(1);

    private final int responseFractionDigits;

    XMode(int responseFractionDigits) {
        this.responseFractionDigits = responseFractionDigits;
    }

    public int getResponseFractionDigits() {
        return responseFractionDigits;
    }

    public double roundForResponse(double value) {
        if (!Double.isFinite(value)) return value;
        return BigDecimal.valueOf(value)
                .setScale(responseFractionDigits, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
