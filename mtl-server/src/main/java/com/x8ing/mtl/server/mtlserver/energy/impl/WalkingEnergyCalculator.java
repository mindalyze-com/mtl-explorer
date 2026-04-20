package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.energy.EnergyCalculator;
import com.x8ing.mtl.server.mtlserver.energy.EnergyComponents;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for walking.
 * <p>
 * At walking speeds (≤ 6 km/h), aerodynamic drag is negligible (< 1% of total energy).
 * Rolling resistance does not apply (no wheels).
 * <p>
 * Components:
 * <ul>
 *   <li>Gravitational PE: m·g·Δh</li>
 *   <li>Kinetic energy change: ½·m·(v₂²−v₁²)</li>
 * </ul>
 * Equipment default: 0 kg
 */
@Component
public class WalkingEnergyCalculator extends EnergyCalculator {

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.WALKING);
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return 0.0;
    }

    @Override
    public EnergyComponents calculateBetweenPoints(GpsTrackDataPoint current, GpsTrackDataPoint prev, EnergyParameters params) {
        if (prev == null) return EnergyComponents.ZERO;

        double distance = segmentDistance(current);
        if (distance <= 0) return EnergyComponents.ZERO;

        double totalMass = params.getTotalMassKg(getDefaultEquipmentWeightKg());

        // Gravity
        double gravity = gravitationalEnergy(totalMass, current.getPointAltitude(), prev.getPointAltitude());

        // Kinetic energy change — use smoothed speed to avoid GPS jitter amplification on v²
        double currentSpeed = smoothedSpeedMps(current, params);
        double prevSpeed = smoothedSpeedMps(prev, params);
        double kinetic = kineticEnergyChange(totalMass, currentSpeed, prevSpeed);

        return EnergyComponents.builder()
                .gravitationalJoules(gravity)
                .kineticJoules(kinetic)
                .build();
    }
}
