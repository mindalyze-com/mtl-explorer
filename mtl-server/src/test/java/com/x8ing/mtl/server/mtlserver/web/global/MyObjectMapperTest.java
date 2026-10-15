package com.x8ing.mtl.server.mtlserver.web.global;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MyObjectMapperTest {
    @Test
    void unsupportedPointResponsesKeepSourceObservationsAndSpatialMetricsButOmitTimingMeasurements() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new DateTimeConfig().jsonCustomizer().customize(builder);
        new MyObjectMapper().mtlJsonMapperCustomizer().customize(builder);
        var mapper = builder.build();
        var point = new com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint();
        var timestamp = java.util.Date.from(Instant.parse("2035-06-01T12:00:00Z"));
        point.setPointTimestamp(timestamp);
        point.setPointLongLat(new GeometryFactory().createPoint(new Coordinate(16, 61, 120)));
        point.setPointAltitude(120.0);
        point.setDistanceInMeterSinceStart(1500.0);
        point.setDurationSinceStart(900.0);
        point.setSpeedInKmhMovingWindow(6.0);
        point.setEnergyCumulativeWh(80.0);
        point.setPowerWatts(140.0);
        var healthy = mapper.readTree(mapper.writeValueAsString(java.util.List.of(point))).get(0);
        assertThat(healthy.get("durationSinceStart").doubleValue()).isEqualTo(900);
        assertThat(healthy.get("powerWatts").doubleValue()).isEqualTo(140);
        point.setTimingUnsupportedForResponse(true);
        var unsupported = mapper.readTree(mapper.writeValueAsString(java.util.List.of(point))).get(0);
        for (String field : java.util.List.of("durationSinceStart", "speedInKmhMovingWindow", "energyCumulativeWh", "powerWatts")) {
            assertThat(unsupported.get(field).isNull()).as(field).isTrue();
        }
        assertThat(unsupported.get("pointTimestamp")).isEqualTo(healthy.get("pointTimestamp"));
        assertThat(unsupported.get("pointLongLat")).isEqualTo(healthy.get("pointLongLat"));
        assertThat(unsupported.get("pointAltitude").doubleValue()).isEqualTo(120);
        assertThat(unsupported.get("distanceInMeterSinceStart").doubleValue()).isEqualTo(1500);
        assertThat(unsupported.has("timingUnsupportedForResponse")).isFalse();
        assertThat(point.getDurationSinceStart()).isEqualTo(900);
        assertThat(point.getPointTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void unavailableTrackMeasurementsAreNullInNestedResponsesWithoutMutatingStoredEvidence() {
        var mapper = JsonMapper.builder().build();
        var track = new com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack();
        track.setTrackName("Synthetic timing evidence");
        track.setTrackLengthInMeter(2000.0);
        track.setTrackDurationInMotionSecs(3600.0);
        track.setSpeedInKmh30sMax(90.0);
        track.setElevationGainPerHour30sMax(800.0);
        track.setEnergyNetTotalWh(400.0);
        track.setPowerWattsAvg(120.0);
        track.setTrackStopCount(4);
        track.setExplorationScore(0.75);
        track.setMotionAnalysisStatus(com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.Status.COMPLETE);
        var healthy = mapper.readTree(mapper.writeValueAsString(Map.of("gpsTrack", track))).get("gpsTrack");
        assertThat(healthy.get("trackDurationInMotionSecs").doubleValue()).isEqualTo(3600);
        assertThat(healthy.get("trackStopCount").intValue()).isEqualTo(4);
        assertThat(healthy.get("explorationScore").doubleValue()).isEqualTo(0.75);
        track.setTimingUnreliable(true);
        var unsupported = mapper.readTree(mapper.writeValueAsString(Map.of("gpsTrack", track))).get("gpsTrack");
        for (String name : java.util.List.of("trackDurationInMotionSecs", "speedInKmh30sMax", "elevationGainPerHour30sMax",
                "energyNetTotalWh", "powerWattsAvg", "trackStopCount", "motionAnalysisStatus", "explorationScore"))
            assertThat(unsupported.get(name).isNull()).as(name).isTrue();
        assertThat(unsupported.get("trackLengthInMeter").doubleValue()).isEqualTo(2000);
        assertThat(track.getTrackDurationInMotionSecs()).isEqualTo(3600);
        assertThat(track.getPowerWattsAvg()).isEqualTo(120);
        assertThat(track.getExplorationScore()).isEqualTo(0.75);
    }

    @Test
    void serializesInstantAsUtcIsoString() throws Exception {
        JsonMapper.Builder builder = JsonMapper.builder();
        new DateTimeConfig().jsonCustomizer().customize(builder);
        new MyObjectMapper().mtlJsonMapperCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();

        String json = mapper.writeValueAsString(Map.of(
                "timestamp", Instant.parse("2026-05-18T06:10:11Z")));

        assertThat(json).isEqualTo("{\"timestamp\":\"2026-05-18T06:10:11Z\"}");
    }

    @Test
    void serializesJtsPointAsGeoJson() throws Exception {
        JsonMapper.Builder builder = JsonMapper.builder();
        new MyObjectMapper().mtlJsonMapperCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();

        String json = mapper.writeValueAsString(
                new GeometryFactory().createPoint(new Coordinate(8.5, 47.5, 430.0)));

        assertThat(json).isEqualTo("{\"type\":\"Point\",\"coordinates\":[8.5,47.5,430.0]}");
    }
}
