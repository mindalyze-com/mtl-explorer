package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;

/**
 * Base for calculators using the shared gravity, drag, optional surface
 * resistance, and kinetic model.
 */
public abstract class StandardEnergyCalculator extends EnergyCalculator {

    protected abstract double getDefaultCd();

    protected abstract double getDefaultFrontalArea();

    protected Double getDefaultResistanceCoefficient() {
        return null;
    }

    @Override
    public final EnergyComponents calculateBetweenPoints(
            GpsTrackDataPoint current,
            GpsTrackDataPoint prev,
            EnergyParameters params
    ) {
        return calculateStandardEnergy(
                current,
                prev,
                params,
                getDefaultCd(),
                getDefaultFrontalArea(),
                getDefaultResistanceCoefficient());
    }
}
