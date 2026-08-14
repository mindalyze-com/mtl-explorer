package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.energy.StandardEnergyCalculator;
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
 * Default constants represent a recreational road cyclist in an upright/relaxed position
 * (CdA = Cd×A = 0.9×0.5 = 0.45 m²). Reference CdA values by riding position:
 * aero tuck/drops ~0.22–0.28, normal drops ~0.28–0.34, hoods/relaxed ~0.35–0.40,
 * upright/casual ~0.40–0.50. Override via {@link EnergyParameters} if needed.
 * Cd=0.9, A=0.5 m², Cr=0.005, equipment=10 kg (road bike + shoes + helmet + water)
 */
@Component
public class BicycleEnergyCalculator extends StandardEnergyCalculator {

    protected static final double DEFAULT_CD = 0.9;             // combined CdA = 0.45 m² (upright/relaxed position)
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
    protected Double getDefaultResistanceCoefficient() {
        return getDefaultCr();
    }
}
