package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonPropertyOrder({"trackIds", "processing"})
public record TrackProximityResponseDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> trackIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TrackProcessingStatusDto processing) {
    public TrackProximityResponseDto {
        trackIds = List.copyOf(trackIds);
    }
}
