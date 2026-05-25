package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for rowing shells.
 */
@Component
public class RowingEnergyCalculator extends WaterSportEnergyCalculator {

    private static final double DEFAULT_CD_TIMES_AREA = 0.035;
    private static final double DEFAULT_EQUIPMENT_KG = 15.0;

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.ROWING);
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
