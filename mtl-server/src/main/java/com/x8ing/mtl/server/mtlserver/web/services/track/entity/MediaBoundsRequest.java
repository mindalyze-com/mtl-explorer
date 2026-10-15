package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonPropertyOrder({"minLat", "minLng", "maxLat", "maxLng", "scope", "trackIds"})
public record MediaBoundsRequest(
        double minLat,
        double minLng,
        double maxLat,
        double maxLng,
        MediaTrendScope scope,
        @Schema(description = "Matching filter track IDs. Their earliest valid start and latest valid end bound media capture times in both scopes.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<Long> trackIds
) {
}
