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
        "normalizedPowerMed",
        "intensityIndexAvg",
        "trainingLoadPerRideAvg",
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
     * Median Normalized Power (W) across tracks in the group. NP is a
     * variability-weighted power that better reflects physiological cost than
     * simple avg power (Strava: Weighted Average Power; Garmin/TrainingPeaks: NP;
     * GoldenCheetah: xPower / IsoPower).
     */
    Double getNormalizedPowerMed();

    /**
     * Average Intensity Index across tracks in the group. Intensity Index =
     * Normalized Power / threshold power. Typical scale: &lt;0.75 easy,
     * 0.75–0.85 endurance, 0.85–0.95 tempo, &gt;0.95 race effort.
     */
    Double getIntensityIndexAvg();

    /**
     * Average Training Load per ride in the group. Training Load =
     * (NP / threshold)² × moving_hours × 100. Inspired by TSS (TrainingPeaks).
     * A 1 h effort at threshold power scores ~100.
     */
    Double getTrainingLoadPerRideAvg();

    /**
     * Average exploration score (0..1) across tracks in the group that have a calculated score.
     */
    Double getExplorationScoreAvg();
}
