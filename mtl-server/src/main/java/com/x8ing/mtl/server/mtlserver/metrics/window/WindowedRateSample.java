package com.x8ing.mtl.server.mtlserver.metrics.window;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.metrics.MetricConstants;

/**
 * Per-point output of {@link PointWindowedRateCalculator}.
 * <p>
 * All fields are nullable — {@code null} signals "window not warm yet" or "no
 * motion in window" (e.g. the leading 30 seconds of a track).
 */
@JsonPropertyOrder({
        "speedInKmh",
        "elevationGainPerHour",
        "elevationLossPerHour"
})
public final class WindowedRateSample {

    private final Double speedInKmh;
    private final Double elevationGainPerHour;
    private final Double elevationLossPerHour;

    public WindowedRateSample(Double speedInKmh, Double elevationGainPerHour, Double elevationLossPerHour) {
        this.speedInKmh = speedInKmh;
        this.elevationGainPerHour = elevationGainPerHour;
        this.elevationLossPerHour = elevationLossPerHour;
    }

    public Double speedInKmh() {
        return speedInKmh;
    }

    public Double elevationGainPerHour() {
        return elevationGainPerHour;
    }

    public Double elevationLossPerHour() {
        return elevationLossPerHour;
    }

    public static WindowedRateSample empty() {
        return new WindowedRateSample(null, null, null);
    }

    static WindowedRateSample fromWindow(double windowSec, double windowDistanceMeter,
                                         double windowAscentMeter, double windowDescentMeter) {
        double speed = Math.min(windowDistanceMeter / windowSec * MetricConstants.MPS_TO_KMH, MetricConstants.MAX_SPEED_KMH);
        double gain = windowAscentMeter > 0
                ? Math.min(windowAscentMeter / windowSec * MetricConstants.SECONDS_PER_HOUR, MetricConstants.MAX_ELEVATION_RATE_PER_HOUR)
                : 0.0;
        double loss = windowDescentMeter > 0
                ? Math.min(windowDescentMeter / windowSec * MetricConstants.SECONDS_PER_HOUR, MetricConstants.MAX_ELEVATION_RATE_PER_HOUR)
                : 0.0;
        return new WindowedRateSample(speed, gain, loss);
    }
}
