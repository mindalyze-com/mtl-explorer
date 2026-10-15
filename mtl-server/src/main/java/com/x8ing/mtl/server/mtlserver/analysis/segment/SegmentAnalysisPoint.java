package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonPropertyOrder({"distanceMeters", "elapsedSeconds", "timestamp", "longitude", "latitude", "speedKmh", "altitudeMeters", "powerWatts", "slopePercent", "energyWh", "breakBefore"})
public record SegmentAnalysisPoint(
        @Schema(nullable = true, description = "Complete traveled distance from the attempt start; null after unsupported path coverage.") Double distanceMeters,
        @Schema(nullable = true) Double elapsedSeconds, @Schema(nullable = true) Instant timestamp,
        double longitude, double latitude,
        @Schema(nullable = true) Double speedKmh, @Schema(nullable = true) Double altitudeMeters,
        @Schema(description = "Display power from the full-track trailing window (60 seconds for walking/hiking, 30 seconds otherwise), averaged within chart buckets. Null during warm-up or unsupported coverage.", nullable = true) Double powerWatts,
        @Schema(nullable = true) Double slopePercent, @Schema(nullable = true) Double energyWh, boolean breakBefore) {
}
