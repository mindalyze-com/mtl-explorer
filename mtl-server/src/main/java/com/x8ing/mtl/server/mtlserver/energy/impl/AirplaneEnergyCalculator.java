package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.energy.EnergyCalculator;
import com.x8ing.mtl.server.mtlserver.energy.EnergyComponents;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for airplane tracks.
 * <p>
 * This is a per-passenger-share external mechanical model, not a whole-aircraft
 * fuel model. It estimates gravity, aerodynamic drag, and kinetic energy from
 * GPS speed/altitude using a default passenger-equivalent total mass of 500 kg
 * (configured rider/person weight plus aircraft mass share). It does not
 * estimate fuel burn, engine efficiency, lift-induced drag, wind, taxi phases,
 * or aircraft-specific configuration.
 */
@Component
public class AirplaneEnergyCalculator extends EnergyCalculator {

    private static final double DEFAULT_CD = 1.0;
    private static final double DEFAULT_FRONTAL_AREA = 0.04; // per-passenger equivalent CdA when Cd=1
    private static final double DEFAULT_AIRCRAFT_MASS_SHARE_KG = 425.0;
    private static final double DEFAULT_MAX_AERO_SPEED_MPS = 340.0;
    private static final double DEFAULT_MAX_POWER_WATTS = 1_000_000.0;
    private static final double DENSITY_SCALE_HEIGHT_M = 8500.0;
    private static final double MAX_DENSITY_ALTITUDE_M = 20_000.0;

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.AIRPLANE);
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return DEFAULT_AIRCRAFT_MASS_SHARE_KG;
    }

    protected double getDefaultCd() {
        return DEFAULT_CD;
    }

    protected double getDefaultFrontalArea() {
        return DEFAULT_FRONTAL_AREA;
    }

    @Override
    protected double getMaxAeroSpeedMps() {
        return DEFAULT_MAX_AERO_SPEED_MPS;
    }

    protected double effectiveDragCoefficient(double cd, double speedMps) {
        return cd;
    }

    @Override
    public double getMaxPowerWatts() {
        return DEFAULT_MAX_POWER_WATTS;
    }

    @Override
    public EnergyComponents calculateBetweenPoints(GpsTrackDataPoint current, GpsTrackDataPoint prev, EnergyParameters params) {
        if (prev == null) return EnergyComponents.ZERO;

        double distance = segmentDistance(current);
        if (distance <= 0) return EnergyComponents.ZERO;

        double totalMass = params.getTotalMassKg(getDefaultEquipmentWeightKg());
        double cd = params.getDragCoefficient(getDefaultCd());
        double area = params.getFrontalArea(getDefaultFrontalArea());
        double rho = airDensityForSegment(current, prev, params);

        double gravity = gravitationalEnergy(totalMass, current.getPointAltitude(), prev.getPointAltitude());

        double speedForDrag = Math.min(smoothedSpeedMps(current, params), getMaxAeroSpeedMps());
        double effectiveCd = effectiveDragCoefficient(cd, speedForDrag);
        double aeroDrag = 0.5 * effectiveCd * area * rho * speedForDrag * speedForDrag * distance;

        double currentSpeed = smoothedSpeedMps(current, params);
        double prevSpeed = smoothedSpeedMps(prev, params);
        double kinetic = kineticEnergyChange(totalMass, currentSpeed, prevSpeed);

        return EnergyComponents.builder()
                .gravitationalJoules(gravity)
                .aeroDragJoules(aeroDrag)
                .kineticJoules(kinetic)
                .build();
    }

    private double airDensityForSegment(GpsTrackDataPoint current, GpsTrackDataPoint prev, EnergyParameters params) {
        if (params.getAirDensityOverride() != null) {
            return params.getAirDensityOverride();
        }

        Double currentAltitude = current.getPointAltitude();
        Double prevAltitude = prev.getPointAltitude();
        if (currentAltitude == null || prevAltitude == null) {
            return params.getAirDensity();
        }

        double altitude = Math.max(0.0, (currentAltitude + prevAltitude) / 2.0);
        altitude = Math.min(altitude, MAX_DENSITY_ALTITUDE_M);
        return EnergyParameters.DEFAULT_AIR_DENSITY * Math.exp(-altitude / DENSITY_SCALE_HEIGHT_M);
    }
}
