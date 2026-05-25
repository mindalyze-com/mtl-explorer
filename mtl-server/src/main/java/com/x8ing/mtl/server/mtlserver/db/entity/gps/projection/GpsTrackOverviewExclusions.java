package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder({
        "highlightExcludedTrackCount",
        "statisticsExcludedTrackCount"
})
public interface GpsTrackOverviewExclusions {

    Long getHighlightExcludedTrackCount();

    Long getStatisticsExcludedTrackCount();
}
