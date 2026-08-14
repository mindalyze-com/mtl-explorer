package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.energy.GravityAndKineticEnergyCalculator;
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
public class WalkingEnergyCalculator extends GravityAndKineticEnergyCalculator {

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.WALKING);
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return 0.0;
    }
}
