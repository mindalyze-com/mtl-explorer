package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto.MEDIA_KIND;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackMediaDto.POSITION_ORIGIN;

import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "mediaKind",
        "fileName",
        "effectiveCapturedAt",
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
        Long trackId,
        Double resolvedLat,
        Double resolvedLng,
        POSITION_ORIGIN positionOrigin,
        boolean estimatedPosition,
        boolean ambiguousMatch,
        Double trackPointTimeDeltaSeconds
) {
}
