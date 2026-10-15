package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@JsonPropertyOrder({"identity", "distanceMeters", "elapsedSeconds", "averageSpeedKmh", "ascentMeters", "descentMeters", "energyWh", "maximumPowerWatts", "availability"})
public record SegmentAttemptSummary(SegmentAttemptIdentity identity, @Schema(nullable = true) Double distanceMeters,
                                    @Schema(nullable = true) Double elapsedSeconds, @Schema(nullable = true) Double averageSpeedKmh,
                                    @Schema(nullable = true) Double ascentMeters, @Schema(nullable = true) Double descentMeters,
                                    @Schema(nullable = true) Double energyWh,
                                    @Schema(description = "Maximum instantaneous estimated power over the exact full-density segment; independent of chart point count and display averaging.", nullable = true) Double maximumPowerWatts,
                                    Map<String, SegmentMetricAvailability> availability) {
}
