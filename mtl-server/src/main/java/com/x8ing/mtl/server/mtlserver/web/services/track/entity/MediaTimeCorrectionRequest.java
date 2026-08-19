package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonPropertyOrder({"mediaIds", "offsetSeconds"})
public record MediaTimeCorrectionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Long> mediaIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int offsetSeconds
) {
}
