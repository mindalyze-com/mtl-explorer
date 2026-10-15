package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Where an attempt was when the reference reached a traveled distance. A positive lead means the attempt was ahead.
 */
@JsonPropertyOrder({"referenceDistanceMeters", "referenceElapsedSeconds", "distanceMeters", "leadMeters", "state",
        "breakBefore", "positionMeaning"})
public record SegmentGhostPoint(double referenceDistanceMeters, @Schema(nullable = true) Double referenceElapsedSeconds,
                                @Schema(nullable = true) Double distanceMeters, @Schema(nullable = true) Double leadMeters, String state,
                                boolean breakBefore, String positionMeaning) {
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String FINISHED = "FINISHED";
    public static final String UNAVAILABLE = "UNAVAILABLE";
}
