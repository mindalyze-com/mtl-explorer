package com.x8ing.mtl.server.mtlserver.gpx;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservative two-sided altitude validation before Gaussian smoothing or stop inference.
 */
public final class ElevationObservationFilter {
    private static final double MAX_REFERENCE_GAP_SECONDS = 60;
    private static final double MIN_SPIKE_METRES = 30;
    private static final double MAX_VERTICAL_RATE_MPS = 10;
    private static final int MAX_SPIKE_POINTS = 5;

    private ElevationObservationFilter() {
    }

    static java.util.Map<String, String> cleaningSettings() {
        return java.util.Map.of("elevation.maximumReferenceGapSeconds", Double.toString(MAX_REFERENCE_GAP_SECONDS),
                "elevation.minimumSpikeMetres", Double.toString(MIN_SPIKE_METRES),
                "elevation.maximumVerticalRateMetresPerSecond", Double.toString(MAX_VERTICAL_RATE_MPS),
                "elevation.maximumSpikePoints", Integer.toString(MAX_SPIKE_POINTS));
    }

    public static List<Coordinate> clean(List<Coordinate> input) {
        List<Coordinate> result = new ArrayList<>(input.size());
        for (Coordinate p : input) result.add(new CoordinateXYZM(p.getX(), p.getY(), p.getZ(), p.getM()));
        // A bounded excursion requires credible observations on BOTH sides. A
        // persistent step cannot be identified as bad altitude using altitude alone.
        for (int first = 1; first < input.size() - 1; first++) {
            Coordinate before = input.get(first - 1);
            Coordinate spike = input.get(first);
            if (!hasAltitudeAndTime(before) || !hasAltitudeAndTime(spike)) continue;
            double entryDt = spike.getM() - before.getM();
            double entryDz = spike.getZ() - before.getZ();
            if (entryDt <= 0 || Math.abs(entryDz) < MIN_SPIKE_METRES
                || Math.abs(entryDz) / entryDt <= MAX_VERTICAL_RATE_MPS) continue;
            for (int afterIndex = first + 1; afterIndex < Math.min(input.size(), first + MAX_SPIKE_POINTS + 1); afterIndex++) {
                Coordinate after = input.get(afterIndex);
                if (!hasAltitudeAndTime(after)) continue;
                double dt = after.getM() - before.getM();
                if (dt <= 0 || dt > MAX_REFERENCE_GAP_SECONDS) break;
                if (Math.abs(after.getZ() - before.getZ()) > MIN_SPIKE_METRES) continue;
                for (int j = first; j < afterIndex; j++) {
                    Coordinate p = result.get(j);
                    // Unknown is explicit; the Kalman model must not receive a made-up measurement.
                    result.set(j, new CoordinateXYZM(p.getX(), p.getY(), Double.NaN, p.getM()));
                }
                first = afterIndex - 1;
                break;
            }
        }
        return result;
    }

    private static boolean hasAltitudeAndTime(Coordinate point) {
        return Double.isFinite(point.getZ()) && Double.isFinite(point.getM());
    }
}
