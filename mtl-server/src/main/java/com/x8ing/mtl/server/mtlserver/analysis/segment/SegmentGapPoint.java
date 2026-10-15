package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder({"distanceMeters", "differenceSeconds", "breakBefore", "positionMeaning"})
public record SegmentGapPoint(double distanceMeters, @Schema(nullable = true) Double differenceSeconds, boolean breakBefore, String positionMeaning) {
}
