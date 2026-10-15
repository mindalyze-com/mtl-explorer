package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@JsonPropertyOrder({"state", "stages", "failedGpsFiles", "checkedAt"})
public record TrackProcessingStatusDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) State state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Stage> stages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Failed GPS imports, separate from pending work.") long failedGpsFiles,
        @Schema(description = "Time of the last processing status update. Status applies to the whole library, not just this filter.") Instant checkedAt) {
    public enum State {READY, RUNNING, UNAVAILABLE}

    public enum Stage {GPS_INDEXING, DUPLICATE_CHECKING, ACTIVITY_CLASSIFICATION}

    public TrackProcessingStatusDto {
        stages = List.copyOf(stages);
    }
}
