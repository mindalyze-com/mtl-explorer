package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import lombok.Data;

import java.util.Date;

@Data
@JsonPropertyOrder({
        "id",
        "gpsTrackDataId",
        "movingWindowInSec",
        "createDate",
        "pointIndex",
        "pointIndexMax",
        "canonicalPointIndex",
        "pointTimestamp",
        "pointLongLat",
        "pointXY",
        "pointAltitude",
        "distanceInMeterBetweenPoints",
        "distanceInMeterSinceStart",
        "durationBetweenPointsInSec",
        "durationSinceStart",
        "ascentInMeterBetweenPoints",
        "ascentInMeterSinceStart",
        "descentInMeterSinceStart",
        "elevationGainPerHourMovingWindow",
        "elevationLossPerHourMovingWindow",
        "speedInKmhMovingWindow",
        "slopePercentageInMovingWindow",
        "energyGravitationalWh",
        "energyAeroDragWh",
        "energyRollingResistanceWh",
        "energyKineticWh",
        "energyTotalWh",
        "energyCumulativeWh",
        "powerWatts"
})
public class GpsTrackDataPointDto {

    private Long id;
    private Long gpsTrackDataId;
    private Integer movingWindowInSec;
    private Date createDate;
    private Integer pointIndex;
    private Integer pointIndexMax;
    private Integer canonicalPointIndex;
    private Date pointTimestamp;
    private GeoPointDto pointLongLat;
    private GeoPointDto pointXY;
    private Double pointAltitude;
    private Double distanceInMeterBetweenPoints;
    private Double distanceInMeterSinceStart;
    private Double durationBetweenPointsInSec;
    private Double durationSinceStart;
    private Double ascentInMeterBetweenPoints;
    private Double ascentInMeterSinceStart;
    private Double descentInMeterSinceStart;
    private Double elevationGainPerHourMovingWindow;
    private Double elevationLossPerHourMovingWindow;
    private Double speedInKmhMovingWindow;
    private Double slopePercentageInMovingWindow;
    private Double energyGravitationalWh;
    private Double energyAeroDragWh;
    private Double energyRollingResistanceWh;
    private Double energyKineticWh;
    private Double energyTotalWh;
    private Double energyCumulativeWh;
    private Double powerWatts;

    public static GpsTrackDataPointDto from(GpsTrackDataPoint point) {
        if (point == null) {
            return null;
        }
        GpsTrackDataPointDto dto = new GpsTrackDataPointDto();
        dto.id = point.getId();
        dto.gpsTrackDataId = point.getGpsTrackDataId();
        dto.movingWindowInSec = point.getMovingWindowInSec();
        dto.createDate = point.getCreateDate();
        dto.pointIndex = point.getPointIndex();
        dto.pointIndexMax = point.getPointIndexMax();
        dto.canonicalPointIndex = point.getCanonicalPointIndex();
        dto.pointTimestamp = point.getPointTimestamp();
        dto.pointLongLat = GeoPointDto.fromLongLat(point.getPointLongLat());
        dto.pointXY = GeoPointDto.from(point.getPointXY());
        dto.pointAltitude = point.getPointAltitude();
        dto.distanceInMeterBetweenPoints = point.getDistanceInMeterBetweenPoints();
        dto.distanceInMeterSinceStart = point.getDistanceInMeterSinceStart();
        dto.durationBetweenPointsInSec = point.getDurationBetweenPointsInSec();
        dto.durationSinceStart = point.getDurationSinceStart();
        dto.ascentInMeterBetweenPoints = point.getAscentInMeterBetweenPoints();
        dto.ascentInMeterSinceStart = point.getAscentInMeterSinceStart();
        dto.descentInMeterSinceStart = point.getDescentInMeterSinceStart();
        dto.elevationGainPerHourMovingWindow = point.getElevationGainPerHourMovingWindow();
        dto.elevationLossPerHourMovingWindow = point.getElevationLossPerHourMovingWindow();
        dto.speedInKmhMovingWindow = point.getSpeedInKmhMovingWindow();
        dto.slopePercentageInMovingWindow = point.getSlopePercentageInMovingWindow();
        dto.energyGravitationalWh = point.getEnergyGravitationalWh();
        dto.energyAeroDragWh = point.getEnergyAeroDragWh();
        dto.energyRollingResistanceWh = point.getEnergyRollingResistanceWh();
        dto.energyKineticWh = point.getEnergyKineticWh();
        dto.energyTotalWh = point.getEnergyTotalWh();
        dto.energyCumulativeWh = point.getEnergyCumulativeWh();
        dto.powerWatts = point.getPowerWatts();
        return dto;
    }
}
