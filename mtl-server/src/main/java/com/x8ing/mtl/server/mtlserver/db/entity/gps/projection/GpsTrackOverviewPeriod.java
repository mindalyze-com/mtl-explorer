package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder({
        "sortOrder",
        "periodType",
        "periodKey",
        "label",
        "trackCount",
        "distanceM",
        "durationMs"
})
public interface GpsTrackOverviewPeriod {

    Integer getSortOrder();

    String getPeriodType();

    String getPeriodKey();

    String getLabel();

    Long getTrackCount();

    Double getDistanceM();

    Double getDurationMs();
}
