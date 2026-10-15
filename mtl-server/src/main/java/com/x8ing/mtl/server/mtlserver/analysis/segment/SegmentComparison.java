package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonPropertyOrder({"identity", "referenceIdentity", "method", "finishDifferenceSeconds", "supportedDistanceMeters", "points",
        "ghostPoints", "unavailableReason"})
public record SegmentComparison(SegmentAttemptIdentity identity, SegmentAttemptIdentity referenceIdentity, String method,
                                @Schema(nullable = true) Double finishDifferenceSeconds, @Schema(nullable = true) Double supportedDistanceMeters,
                                List<SegmentGapPoint> points,
                                List<SegmentGhostPoint> ghostPoints, String unavailableReason) {
}
