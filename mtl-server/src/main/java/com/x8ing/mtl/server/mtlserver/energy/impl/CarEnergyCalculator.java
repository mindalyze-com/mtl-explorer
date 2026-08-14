package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for cars.
 * <p>
 * Defaults represent a typical passenger car road-load model:
 * Cd=0.29, A=2.2 m², Cr=0.012, vehicle mass=1500 kg. The configured rider
 * weight is added as occupant mass. Values can be overridden through
 * {@link com.x8ing.mtl.server.mtlserver.energy.EnergyParameters}.
 */
@Component
public class CarEnergyCalculator extends WheeledMotorVehicleEnergyCalculator {

    private static final double DEFAULT_CD = 0.29;
    private static final double DEFAULT_FRONTAL_AREA = 2.2;
    private static final double DEFAULT_CR = 0.012;
    private static final double DEFAULT_VEHICLE_WEIGHT_KG = 1500.0;
    private static final double DEFAULT_MAX_POWER_WATTS = 250_000.0;

    public CarEnergyCalculator() {
        super(
                DEFAULT_CD,
                DEFAULT_FRONTAL_AREA,
                DEFAULT_CR,
                DEFAULT_VEHICLE_WEIGHT_KG,
                DEFAULT_MAX_POWER_WATTS);
    }

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.CAR);
    }
}
