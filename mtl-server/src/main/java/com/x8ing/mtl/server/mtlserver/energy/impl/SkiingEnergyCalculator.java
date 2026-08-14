package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.energy.StandardEnergyCalculator;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for skiing (downhill and cross-country / ski touring).
 * <p>
 * Components:
 * <ul>
 *   <li>Gravitational PE: m·g·Δh</li>
 *   <li>Aerodynamic drag: ½·Cd·A·ρ·v²·d (Cd=1.1, A=0.6 m² — tucked/semi-tucked)</li>
 *   <li>Snow friction: Cr·m·g·d (Cr=0.04 — waxed ski on packed snow)</li>
 *   <li>Kinetic energy change: ½·m·(v₂²−v₁²)</li>
 * </ul>
 * Equipment default: 8 kg (skis, boots, poles)
 */
@Component
public class SkiingEnergyCalculator extends StandardEnergyCalculator {

    private static final double DEFAULT_CD = 1.1;
    private static final double DEFAULT_AREA = 0.6;    // m²
    private static final double DEFAULT_CR = 0.04;      // waxed ski on packed snow
    private static final double DEFAULT_EQUIPMENT_KG = 8.0;

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.SKIING);
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return DEFAULT_EQUIPMENT_KG;
    }

    @Override
    protected double getDefaultCd() {
        return DEFAULT_CD;
    }

    @Override
    protected double getDefaultFrontalArea() {
        return DEFAULT_AREA;
    }

    @Override
    protected Double getDefaultResistanceCoefficient() {
        return DEFAULT_CR;
    }
}
