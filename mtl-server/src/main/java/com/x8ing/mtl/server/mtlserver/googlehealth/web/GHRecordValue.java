package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

/** Normalized measurement; raw provider fields remain available in the detail payload. */
@JsonPropertyOrder({"value", "unit", "minimum", "maximum"})
public record GHRecordValue(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double value,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unit,
        Double minimum,
        Double maximum
) {
    public GHRecordValue {
        if (!Double.isFinite(value) || unit == null || unit.isBlank()
            || minimum != null && !Double.isFinite(minimum)
            || maximum != null && !Double.isFinite(maximum)) {
            throw new IllegalArgumentException("Health record measurement must have a finite value and a unit");
        }
    }
}
