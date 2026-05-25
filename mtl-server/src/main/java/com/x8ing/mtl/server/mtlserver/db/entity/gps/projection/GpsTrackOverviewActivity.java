package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder({
        "activityType",
        "trackCount",
        "distanceM",
        "durationMs",
        "energyWh"
})
public interface GpsTrackOverviewActivity {

    String getActivityType();

    Long getTrackCount();

    Double getDistanceM();

    Double getDurationMs();

    Double getEnergyWh();
}
