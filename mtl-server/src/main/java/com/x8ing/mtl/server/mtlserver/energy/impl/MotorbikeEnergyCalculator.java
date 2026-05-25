package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for motorbikes.
 * <p>
 * Defaults represent a rider on a road motorbike:
 * Cd=0.60, A=0.70 m², Cr=0.015, motorbike mass=220 kg. The configured rider
 * weight is added as rider mass.
 */
@Component
public class MotorbikeEnergyCalculator extends WheeledMotorVehicleEnergyCalculator {

    private static final double DEFAULT_CD = 0.60;
    private static final double DEFAULT_FRONTAL_AREA = 0.70;
    private static final double DEFAULT_CR = 0.015;
    private static final double DEFAULT_VEHICLE_WEIGHT_KG = 220.0;
    private static final double DEFAULT_MAX_POWER_WATTS = 150_000.0;

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.MOTORBIKING);
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return DEFAULT_VEHICLE_WEIGHT_KG;
    }

    @Override
    protected double getDefaultCd() {
        return DEFAULT_CD;
    }

    @Override
    protected double getDefaultFrontalArea() {
        return DEFAULT_FRONTAL_AREA;
    }

    @Override
    protected double getDefaultCr() {
        return DEFAULT_CR;
    }

    @Override
    public double getMaxPowerWatts() {
        return DEFAULT_MAX_POWER_WATTS;
    }
}
