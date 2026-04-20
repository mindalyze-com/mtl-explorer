package com.x8ing.mtl.server.mtlserver.db.entity.gps;

import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.Point;

import java.util.Date;

@Entity
@Table(name = "gps_track_data_points")
@Data
public class GpsTrackDataPoint {

    // SEQUENCE (not IDENTITY): Hibernate silently disables JDBC batch inserts for IDENTITY generation
    // because it must fire each INSERT immediately to retrieve the DB-generated ID.
    // With SEQUENCE, Hibernate knows the ID before building the INSERT statement, enabling true JDBC
    // batch inserts and unlocking the jdbc.batch_size=100 setting in application.yml.
    //
    // allocationSize=100 must match the DB sequence INCREMENT BY 100 (set in Liquibase 005.xml).
    // Hibernate's pooled optimizer pre-fetches a block of 100 IDs per nextval() call, so a saveAll(500)
    // only needs 5 sequence round-trips instead of 500, and all INSERTs are sent as a single JDBC batch.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gps_track_dp_seq")
    @SequenceGenerator(name = "gps_track_dp_seq", sequenceName = "gps_track_data_points_id_seq", allocationSize = 1000)
    private Long id;

    @Column(name = "gps_track_data_id")
    private Long gpsTrackDataId;

    @Column(name = "moving_window_in_sec")
    private Integer movingWindowInSec;

    @Column(name = "create_date")
    private Date createDate = new Date();

    @Column(name = "point_index")
    private Integer pointIndex;

    @Column(name = "point_index_max")
    private Integer pointIndexMax;

    @Column(name = "point_timestamp")
    private Date pointTimestamp;

    /**
     * WGS 84 is SRID: 4326
     */
    @Column(name = "point_long_lat", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point pointLongLat;

    /**
     * web mercator: "EPSG:3857"
     */
    @Column(name = "point_xy", columnDefinition = "geometry(Point,3857)")
    private Point pointXY;

    @Column(name = "point_altitude")
    private Double pointAltitude;

    @Column(name = "distance_in_meter_between_points")
    private Double distanceInMeterBetweenPoints;

    @Column(name = "distance_in_meter_since_start")
    private Double distanceInMeterSinceStart;

    @Column(name = "duration_between_points_in_sec")
    private Double durationBetweenPointsInSec;

    @Column(name = "duration_since_start")
    private Double durationSinceStart;

    @Column(name = "ascent_in_meter_between_points")
    private Double ascentInMeterBetweenPoints;

    @Column(name = "ascent_in_meter_since_start")
    private Double ascentInMeterSinceStart;

    @Column(name = "descent_in_meter_since_start")
    private Double descentInMeterSinceStart;

    @Column(name = "elevation_gain_per_hour_moving_window")
    private Double elevationGainPerHourMovingWindow;

    @Column(name = "elevation_loss_per_hour_moving_window")
    private Double elevationLossPerHourMovingWindow;

    @Column(name = "speed_in_kmh_moving_window")
    private Double speedInKmhMovingWindow;

    @Column(name = "slope_percentage_in_moving_window")
    private Double slopePercentageInMovingWindow;

    // ── Energy fields (populated by EnergyService, all energy values in Wh, power in W) ──

    /**
     * Per-segment gravitational work in Wh. Positive=ascent, negative=descent.
     */
    @Column(name = "energy_gravitational_wh")
    private Double energyGravitationalWh;

    /**
     * Per-segment aerodynamic/fluid drag work in Wh. Always ≥ 0.
     */
    @Column(name = "energy_aero_drag_wh")
    private Double energyAeroDragWh;

    /**
     * Per-segment rolling/surface friction work in Wh. Always ≥ 0.
     */
    @Column(name = "energy_rolling_resistance_wh")
    private Double energyRollingResistanceWh;

    /**
     * Per-segment kinetic energy change in Wh. Positive=acceleration, negative=braking.
     */
    @Column(name = "energy_kinetic_wh")
    private Double energyKineticWh;

    /**
     * Total positive work for this segment in Wh (sum of positive components).
     */
    @Column(name = "energy_total_wh")
    private Double energyTotalWh;

    /**
     * Cumulative energy since track start in Wh.
     */
    @Column(name = "energy_cumulative_wh")
    private Double energyCumulativeWh;

    /**
     * Instantaneous power output in Watts.
     */
    @Column(name = "power_watts")
    private Double powerWatts;

    /**
     * Round all numeric(12,2) rate/slope fields to 2 decimal places before persisting.
     * Guards against the Kalman smoother collapsing adjacent XY positions to near-zero
     * distances (median ~0.1 m), which can cause slope/rate values to overflow.
     */
    private static final double NUMERIC_12_2_MAX = 9_999_999_999.99;

    @PrePersist
    @PreUpdate
    private void roundNumericFields() {
        elevationGainPerHourMovingWindow = clampAndRound2(elevationGainPerHourMovingWindow);
        elevationLossPerHourMovingWindow = clampAndRound2(elevationLossPerHourMovingWindow);
        speedInKmhMovingWindow = clampAndRound2(speedInKmhMovingWindow);
        slopePercentageInMovingWindow = clampAndRound2(slopePercentageInMovingWindow);
    }

    private static Double clampAndRound2(Double v) {
        if (v == null) return null;
        double clamped = Math.max(-NUMERIC_12_2_MAX, Math.min(v, NUMERIC_12_2_MAX));
        return Math.round(clamped * 100.0) / 100.0;
    }

}
