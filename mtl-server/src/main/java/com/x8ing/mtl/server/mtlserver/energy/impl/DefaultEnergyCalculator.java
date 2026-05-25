package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.energy.EnergyCalculator;
import com.x8ing.mtl.server.mtlserver.energy.EnergyComponents;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
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
public class DefaultEnergyCalculator extends EnergyCalculator {

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of();
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
