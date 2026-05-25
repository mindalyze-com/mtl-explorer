package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Date;

@JsonPropertyOrder({
        "trackCount",
        "distanceM",
        "durationMs",
        "energyWh",
        "oldestStart",
        "newestStart"
})
public interface GpsTrackOverviewSummary {

    Long getTrackCount();

    Double getDistanceM();

    Double getDurationMs();

    Double getEnergyWh();

    Date getOldestStart();

    Date getNewestStart();
}
