package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder({
        "highlightExcludedTrackCount",
        "statisticsExcludedTrackCount",
        "reviewWithheldTrackCount",
        "reviewPendingSourceCount"
})
public interface GpsTrackOverviewExclusions {

    Long getHighlightExcludedTrackCount();

    Long getStatisticsExcludedTrackCount();

    Long getReviewWithheldTrackCount();

    Long getReviewPendingSourceCount();
}
