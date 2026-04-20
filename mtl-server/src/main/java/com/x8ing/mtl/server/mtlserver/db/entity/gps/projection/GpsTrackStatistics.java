package com.x8ing.mtl.server.mtlserver.db.entity.gps.projection;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "groupBy",
        "subGroup",
        "daysWithActivities",
        "numberOfTracks",
        "totalTrackDurationSecs",
        "totalTrackDurationDays",
        "trackDurationSecsMed",
        "trackDurationSecsAvg",
        "trackLengthInMeterSum",
        "trackLengthInMeterMed",
        "trackLengthInMeterAvg",
        "distanceBetweenGpsPointsMed",
        "energyNetTotalWhSum",
        "powerWattsAvgMed",
        "explorationScoreAvg"
})
public interface GpsTrackStatistics {

    String getGroupBy();

    String getSubGroup();

    Long getDaysWithActivities();

    Long getNumberOfTracks();

    Long getTotalTrackDurationSecs();

    Double getTotalTrackDurationDays();

    Integer getTrackDurationSecsMed();

    Integer getTrackDurationSecsAvg();

    Integer getTrackLengthInMeterSum();

    Integer getTrackLengthInMeterMed();

    Integer getTrackLengthInMeterAvg();

    Double getDistanceBetweenGpsPointsMed();

    /**
     * Sum of net energy (Wh) across all tracks in the group.
     */
    Double getEnergyNetTotalWhSum();

    /**
     * Median average power (W) across tracks in the group.
     */
    Double getPowerWattsAvgMed();

    /**
     * Average exploration score (0..1) across tracks in the group that have a calculated score.
     */
    Double getExplorationScoreAvg();
}
