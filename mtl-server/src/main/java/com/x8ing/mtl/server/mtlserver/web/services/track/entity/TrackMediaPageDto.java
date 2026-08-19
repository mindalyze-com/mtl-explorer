package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonPropertyOrder({
        "items",
        "page",
        "pageSize",
        "totalItems",
        "totalPages"
})
public record TrackMediaPageDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<TrackMediaDto> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Zero-based page number.")
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int pageSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long totalItems,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long totalPages) {
}
