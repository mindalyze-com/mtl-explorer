package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.energy.EnergyCalculator;
import com.x8ing.mtl.server.mtlserver.energy.EnergyComponents;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for water sports: kayaking, rowing, stand-up paddle.
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
 * <p>
 * Default constants (kayak-like craft):
 * Cd_water·A_submerged = 0.05 (combined), ρ_water = 1000 kg/m³
 * Equipment: 20 kg (kayak), override for SUP (12 kg) or rowing shell (15 kg)
 */
@Component
public class WaterSportEnergyCalculator extends EnergyCalculator {

    private static final double WATER_DENSITY = 1000.0;       // kg/m³
    private static final double DEFAULT_CD_TIMES_AREA = 0.05;  // Cd·A combined for submerged hull
    private static final double DEFAULT_EQUIPMENT_KG = 20.0;   // kayak weight

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(
                GpsTrack.ACTIVITY_TYPE.KAYAKING,
                GpsTrack.ACTIVITY_TYPE.ROWING,
                GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE
        );
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return DEFAULT_EQUIPMENT_KG;
    }

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
        double cdA = params.getDragCoefficient(1.0) * params.getFrontalArea(DEFAULT_CD_TIMES_AREA);
        double speed = smoothedSpeedMps(current, params);
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
