package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.energy.StandardEnergyCalculator;
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
public class RunningEnergyCalculator extends StandardEnergyCalculator {

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
    protected double getDefaultCd() {
        return DEFAULT_CD;
    }

    @Override
    protected double getDefaultFrontalArea() {
        return DEFAULT_AREA;
    }
}
