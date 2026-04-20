package com.x8ing.mtl.server.mtlserver.energy;

import lombok.Builder;
import lombok.Data;

/**
 * Aggregated energy totals for an entire GPS track.
 * All energy values in Wh. Power in W.
 */
@Data
@Builder
public class TrackEnergySummary {

    /**
     * Total gravitational work on ascent segments (Δh > 0) in Wh. Always ≥ 0.
     */
    @Builder.Default
    private double gravitationalAscentTotalWh = 0;

    /**
     * Total gravitational PE released on descent segments (Δh < 0) in Wh. Always ≥ 0. Informational.
     */
    @Builder.Default
    private double gravitationalDescentTotalWh = 0;

    /**
     * Total aerodynamic/fluid drag work in Wh. Always ≥ 0.
     */
    @Builder.Default
    private double aeroDragTotalWh = 0;

    /**
     * Total rolling/surface friction work in Wh. Always ≥ 0.
     */
    @Builder.Default
    private double rollingResistanceTotalWh = 0;

    /**
     * Total kinetic energy spent accelerating (ΔE_kin > 0) in Wh. Always ≥ 0.
     */
    @Builder.Default
    private double kineticPositiveTotalWh = 0;

    /**
     * Total kinetic energy dissipated braking (ΔE_kin < 0) in Wh. Always ≥ 0. Informational.
     */
    @Builder.Default
    private double kineticNegativeTotalWh = 0;

    /**
     * Net total mechanical energy that must be supplied in Wh.
     */
    @Builder.Default
    private double netEnergyTotalWh = 0;

    /**
     * Average instantaneous power across moving segments (W).
     */
    @Builder.Default
    private double powerWattsAvg = 0;

    /**
     * Average power excluding breaks: netEnergyTotalWh * 3600 / movingTimeSec (W).
     * Only meaningful when movingTimeSec > 0.
     */
    @Builder.Default
    private double powerWattsMovingAvg = 0;

    /**
     * Peak instantaneous power across all segments (W).
     */
    @Builder.Default
    private double powerWattsMax = 0;

    /**
     * Rider weight used for this calculation (audit trail).
     */
    @Builder.Default
    private double weightKgUsed = 0;
}
