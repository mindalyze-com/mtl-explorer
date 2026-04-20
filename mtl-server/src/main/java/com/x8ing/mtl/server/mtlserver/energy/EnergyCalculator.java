package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Abstract base for activity-specific energy calculators.
 * Each subclass declares which {@link GpsTrack.ACTIVITY_TYPE}s it handles
 * and implements the physics formula for a single segment.
 * <p>
 * Provides reusable helper methods for gravitational PE and kinetic energy.
 */
@Component
public abstract class EnergyCalculator {

    /**
     * Which activity types this calculator handles.
     */
    public abstract Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes();

    /**
     * Default equipment weight (kg) for this activity if not overridden in parameters.
     */
    public abstract double getDefaultEquipmentWeightKg();

    public boolean supports(GpsTrack.ACTIVITY_TYPE activityType) {
        return getActivityTypes().contains(activityType);
    }

    /**
     * Calculate the energy components for one segment (from previous point to current point).
     *
     * @param current the current GPS track data point
     * @param prev    the previous GPS track data point (null for the first point)
     * @param params  user/system parameters (weight, optional overrides)
     * @return energy breakdown for this segment; ZERO for the first point or if data is insufficient
     */
    public abstract EnergyComponents calculateBetweenPoints(GpsTrackDataPoint current, GpsTrackDataPoint prev, EnergyParameters params);

    // ────────────────────────────────────────────────────────────────────
    // Constants
    // ────────────────────────────────────────────────────────────────────

    /**
     * Maximum speed (m/s) used for aero drag calculation. ~150 km/h. GPS artifacts above this are clamped.
     */
    protected static final double MAX_SPEED_MPS = 42.0;

    // ────────────────────────────────────────────────────────────────────
    // Shared physics helpers
    // ────────────────────────────────────────────────────────────────────

    /**
     * Gravitational potential energy change: m·g·Δh
     * Returns positive for ascent, negative for descent.
     */
    protected double gravitationalEnergy(double totalMassKg, Double currentAltitude, Double prevAltitude) {
        if (currentAltitude == null || prevAltitude == null) return 0;
        double deltaH = currentAltitude - prevAltitude;
        return totalMassKg * EnergyParameters.GRAVITY * deltaH;
    }

    /**
     * Change in kinetic energy: ½·m·(v₂² − v₁²)
     * Positive = acceleration (energy spent), negative = deceleration.
     */
    protected double kineticEnergyChange(double totalMassKg, double currentSpeedMps, double prevSpeedMps) {
        return 0.5 * totalMassKg * (currentSpeedMps * currentSpeedMps - prevSpeedMps * prevSpeedMps);
    }

    /**
     * Aerodynamic drag energy over a distance: ½·Cd·A·ρ·v²·d
     * Always positive (drag always opposes motion).
     * Speed is clamped to {@link #MAX_SPEED_MPS} to prevent GPS artifact amplification on v².
     *
     * @param cd       drag coefficient
     * @param area     frontal area in m²
     * @param rho      air density in kg/m³
     * @param speedMps average speed in m/s for this segment
     * @param distance distance in meters
     */
    protected double aeroDragEnergy(double cd, double area, double rho, double speedMps, double distance) {
        double clampedSpeed = Math.min(speedMps, MAX_SPEED_MPS);
        return 0.5 * cd * area * rho * clampedSpeed * clampedSpeed * distance;
    }

    /**
     * Rolling/surface resistance energy over a distance: Cr·m·g·d
     * Always positive.
     */
    protected double rollingResistanceEnergy(double cr, double totalMassKg, double distance) {
        return cr * totalMassKg * EnergyParameters.GRAVITY * distance;
    }

    /**
     * Compute instantaneous speed in m/s from a data point's between-point fields.
     * Returns 0 if data is missing or duration is zero.
     */
    protected double speedMps(GpsTrackDataPoint point) {
        if (point == null) return 0;
        Double dist = point.getDistanceInMeterBetweenPoints();
        Double dur = point.getDurationBetweenPointsInSec();
        if (dist == null || dur == null || dur <= 0) return 0;
        return dist / dur;
    }

    /**
     * Compute smoothed speed in m/s. Fallback chain:
     * <ol>
     *   <li>moving-window speed on the point ({@code speedInKmhMovingWindow})</li>
     *   <li>instantaneous speed ({@code distance / duration}) for this segment</li>
     *   <li>track-level average speed injected via
     *       {@link EnergyParameters#getTrackAverageSpeedMpsFallback()}
     *       (used when the GPX has no per-point timestamps, so aero/kinetic
     *       would otherwise collapse to 0)</li>
     *   <li>0</li>
     * </ol>
     */
    protected double smoothedSpeedMps(GpsTrackDataPoint point, EnergyParameters params) {
        if (point == null) return 0;
        Double kmh = point.getSpeedInKmhMovingWindow();
        if (kmh != null && kmh > 0) return kmh / 3.6;
        double inst = speedMps(point);
        if (inst > 0) return inst;
        if (params != null) {
            Double trackAvg = params.getTrackAverageSpeedMpsFallback();
            if (trackAvg != null && trackAvg > 0) return trackAvg;
        }
        return 0;
    }

    /**
     * Get the distance for this segment in meters. Returns 0 if not available.
     */
    protected double segmentDistance(GpsTrackDataPoint point) {
        if (point == null) return 0;
        Double d = point.getDistanceInMeterBetweenPoints();
        return d != null ? d : 0;
    }
}
