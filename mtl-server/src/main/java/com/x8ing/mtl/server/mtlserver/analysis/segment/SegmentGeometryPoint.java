package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonPropertyOrder({"distanceMeters", "elapsedSeconds", "timestamp", "longitude", "latitude", "breakBefore"})
public record SegmentGeometryPoint(
        @Schema(nullable = true, description = "Complete traveled distance from the attempt start; null after unsupported path coverage.") Double distanceMeters,
        @Schema(nullable = true) Double elapsedSeconds, @Schema(nullable = true) Instant timestamp,
        double longitude, double latitude, boolean breakBefore) {
}
