package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.energy.EnergyCalculator;
import com.x8ing.mtl.server.mtlserver.energy.EnergyComponents;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for road cycling.
 * <p>
 * Components:
 * <ul>
 *   <li>Gravitational PE: m·g·Δh</li>
 *   <li>Aerodynamic drag: ½·Cd·A·ρ·v²·d (uses smoothed speed to reduce GPS noise amplification on v²)</li>
 *   <li>Rolling resistance: Cr·m·g·d</li>
 *   <li>Kinetic energy change: ½·m·(v₂²−v₁²)</li>
 * </ul>
 * Default constants for a road cyclist in drops position:
 * Cd=0.9, A=0.5 m², Cr=0.005, equipment=10 kg (road bike + shoes + helmet)
 */
@Component
public class BicycleEnergyCalculator extends EnergyCalculator {

    protected static final double DEFAULT_CD = 0.9;
    protected static final double DEFAULT_FRONTAL_AREA = 0.5;  // m²
    protected static final double DEFAULT_CR = 0.005;           // road tire on asphalt
    protected static final double DEFAULT_EQUIPMENT_KG = 10.0;  // road bike + gear

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.BICYCLE);
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return DEFAULT_EQUIPMENT_KG;
    }

    protected double getDefaultCd() {
        return DEFAULT_CD;
    }

    protected double getDefaultFrontalArea() {
        return DEFAULT_FRONTAL_AREA;
    }

    protected double getDefaultCr() {
        return DEFAULT_CR;
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

        // Gravity
        double gravity = gravitationalEnergy(totalMass, current.getPointAltitude(), prev.getPointAltitude());

        // Aero drag — use smoothed speed (moving window) to avoid GPS noise amplification on v²
        double speedForDrag = smoothedSpeedMps(current, params);
        double aeroDrag = aeroDragEnergy(cd, area, rho, speedForDrag, distance);

        // Rolling resistance
        double rolling = rollingResistanceEnergy(cr, totalMass, distance);

        // Kinetic energy change — use smoothed speed (moving window) to avoid GPS jitter amplification on v²
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
