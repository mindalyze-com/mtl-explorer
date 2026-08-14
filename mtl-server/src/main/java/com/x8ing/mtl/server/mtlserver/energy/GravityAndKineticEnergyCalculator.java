package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;

/**
 * Base for conservative calculators that model only gravity and kinetic change.
 */
public abstract class GravityAndKineticEnergyCalculator extends EnergyCalculator {

    @Override
    public final EnergyComponents calculateBetweenPoints(
            GpsTrackDataPoint current,
            GpsTrackDataPoint prev,
            EnergyParameters params
    ) {
        return calculateGravityAndKineticEnergy(current, prev, params);
    }
}
