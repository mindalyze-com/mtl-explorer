package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentTimingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@JsonPropertyOrder({"reference", "trackId", "fromZone", "fromVisit", "toZone", "toVisit", "start", "end", "startPosition", "endPosition", "startCoordinate", "endCoordinate", "revision", "zoneRevision", "timingStatus"})
public record SegmentAttemptIdentity(String reference, long trackId, String fromZone, int fromVisit, String toZone, int toVisit,
                                     @Schema(nullable = true) Instant start, @Schema(nullable = true) Instant end,
                                     @Schema(nullable = true) Double startPosition, @Schema(nullable = true) Double endPosition,
                                     List<Double> startCoordinate, List<Double> endCoordinate, String revision, String zoneRevision,
                                     SegmentTimingStatus timingStatus) {
}
