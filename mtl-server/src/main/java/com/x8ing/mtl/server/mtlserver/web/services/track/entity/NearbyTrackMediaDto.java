package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder({
        "trackId",
        "distanceMeters",
        "matchedMediaCount"
})
public record NearbyTrackMediaDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long trackId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Shortest distance from the activity route to the selected map point, in meters.")
        double distanceMeters,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Number of selected media correlations for the activity.")
        long matchedMediaCount) {
}
