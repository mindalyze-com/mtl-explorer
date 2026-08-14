package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.energy.StandardEnergyCalculator;

/**
 * Shared physics for wheeled motor vehicles.
 * <p>
 * This models external mechanical road-load work from GPS data: gravity,
 * aerodynamic drag, rolling resistance, and kinetic energy change. It does not
 * estimate fuel or battery energy, drivetrain losses, wind, regenerative
 * braking, or engine efficiency.
 */
@JsonPropertyOrder({
        "defaultCd",
        "defaultFrontalArea",
        "defaultCr"
})
abstract class WheeledMotorVehicleEnergyCalculator extends StandardEnergyCalculator {

    private static final double MAX_ROAD_VEHICLE_AERO_SPEED_MPS = 90.0; // 324 km/h

    private final double defaultCd;
    private final double defaultFrontalArea;
    private final double defaultCr;
    private final double defaultVehicleWeightKg;
    private final double maxPowerWatts;

    protected WheeledMotorVehicleEnergyCalculator(
            double defaultCd,
            double defaultFrontalArea,
            double defaultCr,
            double defaultVehicleWeightKg,
            double maxPowerWatts
    ) {
        this.defaultCd = defaultCd;
        this.defaultFrontalArea = defaultFrontalArea;
        this.defaultCr = defaultCr;
        this.defaultVehicleWeightKg = defaultVehicleWeightKg;
        this.maxPowerWatts = maxPowerWatts;
    }

    @Override
    protected final double getDefaultCd() {
        return defaultCd;
    }

    @Override
    protected final double getDefaultFrontalArea() {
        return defaultFrontalArea;
    }

    @Override
    protected final Double getDefaultResistanceCoefficient() {
        return defaultCr;
    }

    @Override
    public final double getDefaultEquipmentWeightKg() {
        return defaultVehicleWeightKg;
    }

    @Override
    public final double getMaxPowerWatts() {
        return maxPowerWatts;
    }

    @Override
    protected double getMaxAeroSpeedMps() {
        return MAX_ROAD_VEHICLE_AERO_SPEED_MPS;
    }

}
