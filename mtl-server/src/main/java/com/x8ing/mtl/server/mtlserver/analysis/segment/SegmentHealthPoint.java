package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonPropertyOrder({"distanceMeters", "elapsedSeconds", "timestamp", "averageBpm", "sampleCount", "breakBefore"})
public record SegmentHealthPoint(@Schema(nullable = true) Double distanceMeters, double elapsedSeconds, @Schema(nullable = true) Instant timestamp,
                                 @Schema(nullable = true) Double averageBpm, int sampleCount, boolean breakBefore) {
}
