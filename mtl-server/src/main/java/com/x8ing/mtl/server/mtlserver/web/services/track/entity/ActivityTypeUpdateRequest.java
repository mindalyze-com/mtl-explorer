package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import jakarta.validation.constraints.NotNull;

@JsonPropertyOrder({
        "activityType"
})
public record ActivityTypeUpdateRequest(
        @NotNull
        GpsTrack.ACTIVITY_TYPE activityType
) {
}
