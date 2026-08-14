package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.energy.GravityAndKineticEnergyCalculator;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Fallback energy calculator for unclassified or future activity types.
 * Computes only gravitational PE and kinetic energy change as a conservative
 * placeholder when MTL Explorer does not have a useful activity-specific road,
 * trail, water, snow, or aircraft model.
 * <p>
 * It intentionally claims no concrete activity type. {@link
 * com.x8ing.mtl.server.mtlserver.energy.EnergyCalculatorFactory} keeps it as
 * the explicit null/future-type fallback.
 */
@Component
public class DefaultEnergyCalculator extends GravityAndKineticEnergyCalculator {

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of();
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return 0.0;
    }
}
