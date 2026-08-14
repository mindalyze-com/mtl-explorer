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

    public MotorbikeEnergyCalculator() {
        super(
                DEFAULT_CD,
                DEFAULT_FRONTAL_AREA,
                DEFAULT_CR,
                DEFAULT_VEHICLE_WEIGHT_KG,
                DEFAULT_MAX_POWER_WATTS);
    }

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.MOTORBIKING);
    }
}
