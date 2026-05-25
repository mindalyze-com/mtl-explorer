package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;

@JsonPropertyOrder({
        "highlightExclusionReason",
        "statisticsExclusionReason"
})
public record StatisticsExclusionUpdateRequest(
        GpsTrack.STATISTICS_EXCLUSION_REASON highlightExclusionReason,
        GpsTrack.STATISTICS_EXCLUSION_REASON statisticsExclusionReason
) {
}
