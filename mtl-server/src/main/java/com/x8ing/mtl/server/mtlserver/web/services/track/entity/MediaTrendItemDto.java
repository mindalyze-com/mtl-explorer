package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto.MEDIA_KIND;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto.POSITION_ORIGIN;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto.TIME_SOURCE;

import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "mediaKind",
        "fileName",
        "effectiveCapturedAt",
        "appliedCameraOffsetSeconds",
        "timeSource",
        "trackId",
        "resolvedLat",
        "resolvedLng",
        "positionOrigin",
        "estimatedPosition",
        "ambiguousMatch",
        "trackPointTimeDeltaSeconds"
})
public record MediaTrendItemDto(
        long id,
        MEDIA_KIND mediaKind,
        String fileName,
        Date effectiveCapturedAt,
        Integer appliedCameraOffsetSeconds,
        TIME_SOURCE timeSource,
        Long trackId,
        Double resolvedLat,
        Double resolvedLng,
        POSITION_ORIGIN positionOrigin,
        boolean estimatedPosition,
        boolean ambiguousMatch,
        Double trackPointTimeDeltaSeconds
) {
}
