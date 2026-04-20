package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.energy.EnergyCalculator;
import com.x8ing.mtl.server.mtlserver.energy.EnergyComponents;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Fallback energy calculator for motorized and unclassified activities.
 * Computes only gravitational PE and kinetic energy change — informational only,
 * since the engine/motor supplies the mechanical work rather than the person.
 */
@Component
public class DefaultEnergyCalculator extends EnergyCalculator {

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(
                GpsTrack.ACTIVITY_TYPE.CAR,
                GpsTrack.ACTIVITY_TYPE.MOTORBIKING,
                GpsTrack.ACTIVITY_TYPE.AIRPLANE,
                GpsTrack.ACTIVITY_TYPE.SUPER_SONIC
        );
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
