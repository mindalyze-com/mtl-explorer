package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for hiking — same physics as walking
 * but with a default backpack weight of 8 kg.
 */
@Component
public class HikingEnergyCalculator extends WalkingEnergyCalculator {

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.HIKING);
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return 8.0; // typical day-hike backpack
    }
}
