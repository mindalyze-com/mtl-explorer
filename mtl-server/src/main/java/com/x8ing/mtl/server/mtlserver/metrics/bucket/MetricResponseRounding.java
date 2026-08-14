package com.x8ing.mtl.server.mtlserver.metrics.bucket;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class MetricResponseRounding {

    private MetricResponseRounding() {
    }

    static Double round(Double value, int fractionDigits) {
        return value == null ? null : round(value.doubleValue(), fractionDigits);
    }

    static double round(double value, int fractionDigits) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return BigDecimal.valueOf(value)
                .setScale(fractionDigits, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
