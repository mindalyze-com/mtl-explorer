package com.x8ing.mtl.server.mtlserver.metrics.window;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Trailing-window summary for an estimated power series.
 * <p>
 * {@code rollingPowerWatts} mirrors the input length and contains the trailing
 * window-average power at each point (or {@code null} for points before the
 * window has fully warmed up). The track-level peak rolling power and
 * Normalized Power (NP) are also derived in a single pass.
 */
@JsonPropertyOrder({
        "rollingPowerWatts",
        "maxRollingPowerWatts",
        "normalizedPowerWatts"
})
public final class PowerWindowStats {

    private final Double[] rollingPowerWatts;
    private final double maxRollingPowerWatts;
    private final double normalizedPowerWatts;

    public PowerWindowStats(Double[] rollingPowerWatts, double maxRollingPowerWatts, double normalizedPowerWatts) {
        this.rollingPowerWatts = rollingPowerWatts;
        this.maxRollingPowerWatts = maxRollingPowerWatts;
        this.normalizedPowerWatts = normalizedPowerWatts;
    }

    public Double[] rollingPowerWatts() {
        return rollingPowerWatts;
    }

    public double maxRollingPowerWatts() {
        return maxRollingPowerWatts;
    }

    public double normalizedPowerWatts() {
        return normalizedPowerWatts;
    }

    public static PowerWindowStats empty() {
        return new PowerWindowStats(new Double[0], 0, 0);
    }
}
