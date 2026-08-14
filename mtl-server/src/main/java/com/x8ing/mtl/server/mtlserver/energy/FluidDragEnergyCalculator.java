package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;

/**
 * Base for calculators that combine gravity and kinetic energy with one
 * activity-specific fluid-drag model.
 */
public abstract class FluidDragEnergyCalculator extends EnergyCalculator {

    @Override
    public final EnergyComponents calculateBetweenPoints(
            GpsTrackDataPoint current,
            GpsTrackDataPoint prev,
            EnergyParameters params
    ) {
        if (prev == null) return EnergyComponents.ZERO;

        double distance = segmentDistance(current);
        if (distance <= 0) return EnergyComponents.ZERO;

        double totalMass = params.getTotalMassKg(getDefaultEquipmentWeightKg());
        double gravity = gravitationalEnergy(totalMass, current.getPointAltitude(), prev.getPointAltitude());
        double drag = calculateDragJoules(current, prev, params, distance);
        double kinetic = kineticEnergyChange(
                totalMass,
                smoothedSpeedMps(current, params),
                smoothedSpeedMps(prev, params));

        return EnergyComponents.builder()
                .gravitationalJoules(gravity)
                .aeroDragJoules(drag)
                .kineticJoules(kinetic)
                .build();
    }

    protected abstract double calculateDragJoules(
            GpsTrackDataPoint current,
            GpsTrackDataPoint prev,
            EnergyParameters params,
            double distance
    );
}
