package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.energy.EnergyCalculator;
import com.x8ing.mtl.server.mtlserver.energy.EnergyComponents;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;

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
abstract class WheeledMotorVehicleEnergyCalculator extends EnergyCalculator {

    private static final double MAX_ROAD_VEHICLE_AERO_SPEED_MPS = 90.0; // 324 km/h

    protected abstract double getDefaultCd();

    protected abstract double getDefaultFrontalArea();

    protected abstract double getDefaultCr();

    @Override
    protected double getMaxAeroSpeedMps() {
        return MAX_ROAD_VEHICLE_AERO_SPEED_MPS;
    }

    @Override
    public EnergyComponents calculateBetweenPoints(GpsTrackDataPoint current, GpsTrackDataPoint prev, EnergyParameters params) {
        if (prev == null) return EnergyComponents.ZERO;

        double distance = segmentDistance(current);
        if (distance <= 0) return EnergyComponents.ZERO;

        double totalMass = params.getTotalMassKg(getDefaultEquipmentWeightKg());
        double cd = params.getDragCoefficient(getDefaultCd());
        double area = params.getFrontalArea(getDefaultFrontalArea());
        double cr = params.getRollingCoefficient(getDefaultCr());
        double rho = params.getAirDensity();

        double gravity = gravitationalEnergy(totalMass, current.getPointAltitude(), prev.getPointAltitude());

        double speedForDrag = smoothedSpeedMps(current, params);
        double aeroDrag = aeroDragEnergy(cd, area, rho, speedForDrag, distance);

        double rolling = rollingResistanceEnergy(cr, totalMass, distance);

        double currentSpeed = smoothedSpeedMps(current, params);
        double prevSpeed = smoothedSpeedMps(prev, params);
        double kinetic = kineticEnergyChange(totalMass, currentSpeed, prevSpeed);

        return EnergyComponents.builder()
                .gravitationalJoules(gravity)
                .aeroDragJoules(aeroDrag)
                .rollingResistanceJoules(rolling)
                .kineticJoules(kinetic)
                .build();
    }
}
