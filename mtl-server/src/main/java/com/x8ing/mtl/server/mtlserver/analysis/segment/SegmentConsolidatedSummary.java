package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder({"fromZone", "toZone", "visitCount", "elapsedCount", "distanceCount", "speedCount", "meanElapsedSeconds", "meanDistanceMeters", "meanVisitSpeedKmh"})
public record SegmentConsolidatedSummary(String fromZone, String toZone, int visitCount, int elapsedCount, int distanceCount, int speedCount,
                                         @Schema(nullable = true) Double meanElapsedSeconds, @Schema(nullable = true) Double meanDistanceMeters,
                                         @Schema(nullable = true) Double meanVisitSpeedKmh) {
}
