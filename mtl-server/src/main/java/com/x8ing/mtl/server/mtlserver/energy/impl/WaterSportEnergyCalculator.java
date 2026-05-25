package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.energy.EnergyCalculator;
import com.x8ing.mtl.server.mtlserver.energy.EnergyComponents;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;

import java.util.Set;

/**
 * Base energy calculator for human-powered water sports.
 * <p>
 * Components:
 * <ul>
 *   <li>Gravitational PE: m·g·Δh (rivers with elevation change)</li>
 *   <li>Water drag: ½·Cd·A_submerged·ρ_water·v²·d</li>
 *   <li>Kinetic energy change: ½·m·(v₂²−v₁²)</li>
 * </ul>
 * Water drag replaces aero drag — water is ~800× denser than air, but
 * only the submerged portion of the craft matters. The product Cd·A_submerged
 * is much smaller (typically 0.02–0.1 m²·Cd) but ρ_water compensates.
 * This remains a simple quadratic drag estimate and does not model wave drag
 * near displacement hull speed.
 */
@JsonPropertyOrder({
        "activityTypes",
        "defaultCdTimesArea"
})
public abstract class WaterSportEnergyCalculator extends EnergyCalculator {

    protected static final double WATER_DENSITY = 1000.0;       // kg/m³
    private static final double DEFAULT_MAX_WATER_SPEED_MPS = 12.0;

    @Override
    public abstract Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes();

    protected abstract double getDefaultCdTimesArea();

    @Override
    public EnergyComponents calculateBetweenPoints(GpsTrackDataPoint current, GpsTrackDataPoint prev, EnergyParameters params) {
        if (prev == null) return EnergyComponents.ZERO;

        double distance = segmentDistance(current);
        if (distance <= 0) return EnergyComponents.ZERO;

        double totalMass = params.getTotalMassKg(getDefaultEquipmentWeightKg());

        // Gravity (relevant for river descent/ascent sections)
        double gravity = gravitationalEnergy(totalMass, current.getPointAltitude(), prev.getPointAltitude());

        // Water drag: ½ · (Cd·A) · ρ_water · v² · d
        // Use the combined Cd·A product — user can override via dragCoefficient and frontalArea
        double cdA = params.getDragCoefficient(1.0) * params.getFrontalArea(getDefaultCdTimesArea());
        double speed = Math.min(smoothedSpeedMps(current, params), DEFAULT_MAX_WATER_SPEED_MPS);
        double waterDrag = 0.5 * cdA * WATER_DENSITY * speed * speed * distance;

        // Kinetic energy change — use smoothed speed to avoid GPS jitter amplification on v²
        double currentSpeed = smoothedSpeedMps(current, params);
        double prevSpeed = smoothedSpeedMps(prev, params);
        double kinetic = kineticEnergyChange(totalMass, currentSpeed, prevSpeed);

        return EnergyComponents.builder()
                .gravitationalJoules(gravity)
                .aeroDragJoules(waterDrag) // stored in aero_drag field (represents fluid drag — water in this case)
                .kineticJoules(kinetic)
                .build();
    }
}
