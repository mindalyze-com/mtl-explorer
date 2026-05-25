package com.x8ing.mtl.server.mtlserver.energy.impl;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Energy calculator for explicitly supersonic aircraft-like tracks.
 * <p>
 * In MTL Explorer this activity type is also used as a suspicious-track signal
 * when speed is implausibly high, so these values should still be treated as
 * rough diagnostics rather than reliable performance data. A coarse wave-drag
 * multiplier is applied above transonic speeds so drag does not stay at the
 * subsonic aircraft coefficient.
 */
@Component
public class SupersonicEnergyCalculator extends AirplaneEnergyCalculator {

    private static final double DEFAULT_CD = 1.0;
    private static final double DEFAULT_FRONTAL_AREA = 0.08;
    private static final double DEFAULT_AIRCRAFT_MASS_SHARE_KG = 900.0;
    private static final double DEFAULT_MAX_AERO_SPEED_MPS = 900.0;
    private static final double DEFAULT_MAX_POWER_WATTS = 5_000_000.0;
    private static final double TRANSONIC_START_MPS = 280.0;
    private static final double MACH_ONE_REFERENCE_MPS = 340.0;
    private static final double WAVE_DRAG_MULTIPLIER = 2.2;

    @Override
    public Set<GpsTrack.ACTIVITY_TYPE> getActivityTypes() {
        return Set.of(GpsTrack.ACTIVITY_TYPE.SUPER_SONIC);
    }

    @Override
    public double getDefaultEquipmentWeightKg() {
        return DEFAULT_AIRCRAFT_MASS_SHARE_KG;
    }

    @Override
    protected double getDefaultCd() {
        return DEFAULT_CD;
    }

    @Override
    protected double getDefaultFrontalArea() {
        return DEFAULT_FRONTAL_AREA;
    }

    @Override
    protected double getMaxAeroSpeedMps() {
        return DEFAULT_MAX_AERO_SPEED_MPS;
    }

    @Override
    protected double effectiveDragCoefficient(double cd, double speedMps) {
        if (speedMps <= TRANSONIC_START_MPS) {
            return cd;
        }
        if (speedMps >= MACH_ONE_REFERENCE_MPS) {
            return cd * WAVE_DRAG_MULTIPLIER;
        }

        double transonicProgress = (speedMps - TRANSONIC_START_MPS) / (MACH_ONE_REFERENCE_MPS - TRANSONIC_START_MPS);
        return cd * (1.0 + transonicProgress * (WAVE_DRAG_MULTIPLIER - 1.0));
    }

    @Override
    public double getMaxPowerWatts() {
        return DEFAULT_MAX_POWER_WATTS;
    }
}
