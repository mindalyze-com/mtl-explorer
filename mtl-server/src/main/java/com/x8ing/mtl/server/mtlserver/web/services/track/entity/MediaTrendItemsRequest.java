package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonPropertyOrder({
        "grouping",
        "scope",
        "bucketKey",
        "kind",
        "trackIds",
        "page",
        "pageSize"
})
public record MediaTrendItemsRequest(
        MediaTrendGrouping grouping,
        MediaTrendScope scope,
        String bucketKey,
        MediaTrendKindFilter kind,
        @Schema(description = "Matching filter track IDs, required in both scopes. Uses the same capture-time range as media trends and map markers.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<Long> trackIds,
        Integer page,
        Integer pageSize
) {
}
