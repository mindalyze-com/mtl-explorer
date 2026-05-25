package com.x8ing.mtl.server.mtlserver.metrics.window;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Computes a trailing-window rolling-average power series, plus the
 * single-pass derived quantities track-peak rolling power and Normalized
 * Power (NP, Coggan).
 * <p>
 * NP is defined as the 4th root of the time-weighted mean of the
 * {@code windowSec}-trailing rolling-average power raised to the 4th power.
 * The window length is fixed by convention at 30 seconds for cycling NP, so a
 * dedicated 30s constructor is provided alongside the configurable one (the
 * latter is mainly useful for tests).
 * <p>
 * Inputs are plain {@code double[]} arrays so that callers can drive this
 * from either canonical entities or synthetic test streams — no JPA or
 * service dependencies.
 */
@JsonPropertyOrder({
        "windowSec"
})
public final class PointWindowedPowerCalculator {

    /**
     * Fixed Normalized-Power window length per Coggan's definition (seconds).
     */
    public static final double NORMALIZED_POWER_WINDOW_SEC = 30.0;

    private final double windowSec;

    public PointWindowedPowerCalculator() {
        this(NORMALIZED_POWER_WINDOW_SEC);
    }

    public PointWindowedPowerCalculator(double windowSec) {
        if (windowSec <= 0) throw new IllegalArgumentException("windowSec must be > 0");
        this.windowSec = windowSec;
    }

    /**
     * Run the trailing-window power calculation. The two arrays must have the
     * same length; the {@code i}-th entry represents the segment ending at
     * point {@code i}.
     *
     * @param powersWatts  per-segment instantaneous power (W); negative or NaN
     *                     values are clamped to 0.
     * @param durationsSec per-segment duration (s); non-positive or NaN values
     *                     are treated as 0.
     */
    public PowerWindowStats compute(double[] powersWatts, double[] durationsSec) {
        if (powersWatts == null || durationsSec == null
            || powersWatts.length == 0 || powersWatts.length != durationsSec.length) {
            return PowerWindowStats.empty();
        }

        final int n = powersWatts.length;
        Double[] rollingPowerWatts = new Double[n];

        double winPowerDuration = 0;
        double winDuration = 0;
        int windowStart = 0;

        double weightedFourthPowerSum = 0;
        double totalDuration = 0;
        double maxRollingPower = 0;

        for (int i = 0; i < n; i++) {
            double d = durationsSec[i] > 0 ? durationsSec[i] : 0;
            double p = powersWatts[i] > 0 ? powersWatts[i] : 0;
            winPowerDuration += p * d;
            winDuration += d;

            while (windowStart < i) {
                double headD = durationsSec[windowStart] > 0 ? durationsSec[windowStart] : 0;
                if (winDuration - headD >= windowSec) {
                    double headP = powersWatts[windowStart] > 0 ? powersWatts[windowStart] : 0;
                    winPowerDuration -= headP * headD;
                    winDuration -= headD;
                    windowStart++;
                } else {
                    break;
                }
            }

            if (winDuration >= windowSec && d > 0) {
                double rollingAvg = winPowerDuration / winDuration;
                rollingPowerWatts[i] = rollingAvg;
                if (rollingAvg > maxRollingPower) maxRollingPower = rollingAvg;
                double avg4 = rollingAvg * rollingAvg * rollingAvg * rollingAvg;
                weightedFourthPowerSum += avg4 * d;
                totalDuration += d;
            }
        }

        double normalizedPower = totalDuration > 0
                ? Math.pow(weightedFourthPowerSum / totalDuration, 0.25)
                : 0;
        return new PowerWindowStats(rollingPowerWatts, maxRollingPower, normalizedPower);
    }

    public double windowSec() {
        return windowSec;
    }
}
