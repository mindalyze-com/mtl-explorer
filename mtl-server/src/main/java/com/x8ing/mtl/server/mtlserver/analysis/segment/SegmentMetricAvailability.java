package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder({"state", "reason", "coveredSeconds"})
public record SegmentMetricAvailability(State state, String reason, @Schema(nullable = true) Double coveredSeconds) {
    static final String PERFORMANCE_METRIC = "performance";
    static final String DISTANCE_MISMATCH = "DISTANCE_MISMATCH";

    public enum State {AVAILABLE, PARTIAL, UNAVAILABLE}
}
