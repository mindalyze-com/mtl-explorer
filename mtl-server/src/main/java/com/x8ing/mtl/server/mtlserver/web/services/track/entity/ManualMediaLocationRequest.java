package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder({"latitude", "longitude", "note"})
public record ManualMediaLocationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double latitude,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double longitude,
        String note
) {
}
