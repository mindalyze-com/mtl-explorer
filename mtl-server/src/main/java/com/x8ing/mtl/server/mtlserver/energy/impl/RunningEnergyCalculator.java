package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.energy.EnergyCalculator;
import com.x8ing.mtl.server.mtlserver.energy.EnergyComponents;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for running.
 * <p>
 * At running speeds (8–20 km/h), aerodynamic drag becomes non-negligible
 * (~5-15% of total energy depending on speed and headwind).
 * <p>
 * Components:
 * <ul>
 *   <li>Gravitational PE: m·g·Δh</li>
 *   <li>Aerodynamic drag: ½·Cd·A·ρ·v²·d (Cd=1.1 upright body, A=0.5 m²)</li>
 *   <li>Kinetic energy change: ½·m·(v₂²−v₁²)</li>
 * </ul>
 * Equipment default: 0 kg
 */
@Component
public class RunningEnergyCalculator extends EnergyCalculator {

    private static final double DEFAULT_CD = 1.1;    // upright human body
    private static final double DEFAULT_AREA = 0.5;   // m²
    private static final double DEFAULT_EQUIPMENT_KG = 0.0;

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.RUNNING);
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return DEFAULT_EQUIPMENT_KG;
    }

    @Override
    public EnergyComponents calculateBetweenPoints(GpsTrackDataPoint current, GpsTrackDataPoint prev, EnergyParameters params) {
        if (prev == null) return EnergyComponents.ZERO;

        double distance = segmentDistance(current);
        if (distance <= 0) return EnergyComponents.ZERO;

        double totalMass = params.getTotalMassKg(getDefaultEquipmentWeightKg());
        double cd = params.getDragCoefficient(DEFAULT_CD);
        double area = params.getFrontalArea(DEFAULT_AREA);
        double rho = params.getAirDensity();

        // Gravity
        double gravity = gravitationalEnergy(totalMass, current.getPointAltitude(), prev.getPointAltitude());

        // Aero drag — use smoothed speed
        double speedForDrag = smoothedSpeedMps(current, params);
        double aeroDrag = aeroDragEnergy(cd, area, rho, speedForDrag, distance);

        // Kinetic energy change — use smoothed speed to avoid GPS jitter amplification on v²
        double currentSpeed = smoothedSpeedMps(current, params);
        double prevSpeed = smoothedSpeedMps(prev, params);
        double kinetic = kineticEnergyChange(totalMass, currentSpeed, prevSpeed);

        return EnergyComponents.builder()
                .gravitationalJoules(gravity)
                .aeroDragJoules(aeroDrag)
                .kineticJoules(kinetic)
                .build();
    }
}
