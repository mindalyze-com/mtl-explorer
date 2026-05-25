package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for stand-up paddle boards.
 */
@Component
public class StandUpPaddleEnergyCalculator extends WaterSportEnergyCalculator {

    private static final double DEFAULT_CD_TIMES_AREA = 0.08;
    private static final double DEFAULT_EQUIPMENT_KG = 12.0;

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE);
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return DEFAULT_EQUIPMENT_KG;
    }

    @Override
    protected double getDefaultCdTimesArea() {
        return DEFAULT_CD_TIMES_AREA;
    }
}
