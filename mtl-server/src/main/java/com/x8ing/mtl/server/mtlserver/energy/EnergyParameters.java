package com.x8ing.mtl.server.mtlserver.energy;

import lombok.Builder;
import lombok.Data;

/**
 * Parameters for energy calculation. Combines user-specific data (weight) with
 * physics constants that can optionally be overridden.
 * <p>
 * Default physics values are used when not explicitly set (null = use calculator's built-in default).
 */
@Data
@Builder(toBuilder = true)
public class EnergyParameters {

    public static final double DEFAULT_RIDER_WEIGHT_KG = 75.0;
    public static final double DEFAULT_EQUIPMENT_WEIGHT_KG = 0.0;
    public static final double DEFAULT_AIR_DENSITY = 1.225; // kg/m³ at sea level, 15°C
    public static final double GRAVITY = 9.81; // m/s²

    /**
     * Rider/person body weight in kg
     */
    @Builder.Default
    private double riderWeightKg = DEFAULT_RIDER_WEIGHT_KG;

    /**
     * Equipment weight in kg (bike, backpack, kayak, etc.). If null, calculator uses its own default.
     */
    private Double equipmentWeightKgOverride;

    /**
     * Override air density (kg/m³). Null = use DEFAULT_AIR_DENSITY.
     */
    private Double airDensityOverride;

    /**
     * Override drag coefficient. Null = use calculator default.
     */
    private Double dragCoefficientOverride;

    /**
     * Override frontal area in m². Null = use calculator default.
     */
    private Double frontalAreaOverride;

    /**
     * Override rolling/friction coefficient. Null = use calculator default.
     */
    private Double rollingCoefficientOverride;

    /**
     * Track-level average speed in m/s, used as a last-resort fallback inside
     * {@code EnergyCalculator.smoothedSpeedMps()} when a point has neither
     * moving-window speed nor per-segment distance/duration (typical for GPX
     * files without per-point {@code <time>} elements). Injected by
     * {@code EnergyService} before running the per-segment loop.
     * Null or ≤0 means “no fallback, behave as before”.
     */
    private Double trackAverageSpeedMpsFallback;

    public double getAirDensity() {
        return airDensityOverride != null ? airDensityOverride : DEFAULT_AIR_DENSITY;
    }

    /**
     * Total mass = rider + equipment
     */
    public double getTotalMassKg(double defaultEquipmentKg) {
        double equipment = equipmentWeightKgOverride != null ? equipmentWeightKgOverride : defaultEquipmentKg;
        return riderWeightKg + equipment;
    }

    public double getDragCoefficient(double defaultCd) {
        return dragCoefficientOverride != null ? dragCoefficientOverride : defaultCd;
    }

    public double getFrontalArea(double defaultArea) {
        return frontalAreaOverride != null ? frontalAreaOverride : defaultArea;
    }

    public double getRollingCoefficient(double defaultCr) {
        return rollingCoefficientOverride != null ? rollingCoefficientOverride : defaultCr;
    }
}
