package com.x8ing.mtl.server.mtlserver.metrics.chart;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.MetricKey;

/**
 * Runtime metadata for a chart metric key. Exposed with chart-series responses
 * so clients can inspect the metric catalogue without duplicating descriptions
 * or transport precision rules.
 */
@JsonPropertyOrder({
        "key",
        "description",
        "unit",
        "responseFractionDigits"
})
public record MetricDefinition(MetricKey key,
                               String description,
                               String unit,
                               int responseFractionDigits) {

    public static MetricDefinition from(MetricKey key) {
        return new MetricDefinition(
                key,
                key.getDescription(),
                key.getUnit(),
                key.getResponseFractionDigits());
    }
}
