package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonPropertyOrder({
        "grouping",
        "scope",
        "trackIds"
})
public record MediaTrendRequest(
        MediaTrendGrouping grouping,
        MediaTrendScope scope,
        @Schema(description = "Matching filter track IDs, required in both scopes. Media uses the continuous range from their earliest valid start to latest valid end. Empty or undated track selections return no media.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<Long> trackIds
) {
}
