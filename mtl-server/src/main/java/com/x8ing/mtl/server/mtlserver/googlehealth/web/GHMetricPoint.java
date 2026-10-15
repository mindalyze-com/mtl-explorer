package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({
        "time",
        "value",
        "minimum",
        "maximum"
})
public record GHMetricPoint(
        Instant time,
        double value,
        Double minimum,
        Double maximum
) {
}
