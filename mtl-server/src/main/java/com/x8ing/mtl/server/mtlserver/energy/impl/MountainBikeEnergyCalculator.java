package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for mountain biking.
 * Extends road cycling with higher rolling resistance (knobbly tires on trail),
 * larger frontal area + drag coefficient (more upright posture), and heavier equipment.
 * <p>
 * Cd=1.1 (upright trail position), A=0.6 m², Cr=0.015 (off-road), equipment=14 kg (MTB + gear)
 */
@Component
public class MountainBikeEnergyCalculator extends BicycleEnergyCalculator {

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING);
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return 14.0;
    }

    @Override
    protected double getDefaultCr() {
        return 0.015; // knobbly tire on trail
    }

    @Override
    protected double getDefaultCd() {
        return 1.1; // upright trail position (vs. 0.9 for road drops)
    }

    @Override
    protected double getDefaultFrontalArea() {
        return 0.6; // more upright position
    }
}
