package com.x8ing.mtl.server.mtlserver.gpx;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackStopDetector;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conservative isolated-spike cleanup. Motion analysis runs later, on accepted denoised geometry.
 */
@Slf4j
public final class BreakStopTreatment {

    static final String CORRECTOR_NAME = "BreakStopTreatment";

    static final double MIN_STOP_DURATION_S = TrackStopDetector.MIN_STOP_DURATION_S;

    static final double SPIKE_JUMP_M = 90.0;
    static final double SPIKE_RETURN_M = 50.0;
    static final double SPIKE_MAX_DT_S = 40.0;
    static final double SPIKE_SUPPORT_RETURN_M = 40.0;
    static final double UNSUPPORTED_SPIKE_MAX_TOTAL_DT_S = 20.0;

    private BreakStopTreatment() {
    }

    @JsonPropertyOrder({
            "cleanedCoordinates",
            "stops",
            "spikesRemoved",
            "collapsedPoints"
    })
    public record Result(List<Coordinate> cleanedCoordinates,
                         List<TrackStopDetector.StopRange> stops,
                         int spikesRemoved,
                         int collapsedPoints) {
    }

    static java.util.Map<String, String> cleaningSettings() {
        return java.util.Map.of("isolatedPosition.jumpMetres", Double.toString(SPIKE_JUMP_M),
                "isolatedPosition.returnMetres", Double.toString(SPIKE_RETURN_M),
                "isolatedPosition.maximumLegSeconds", Double.toString(SPIKE_MAX_DT_S),
                "isolatedPosition.supportReturnMetres", Double.toString(SPIKE_SUPPORT_RETURN_M),
                "isolatedPosition.maximumUnsupportedSeconds", Double.toString(UNSUPPORTED_SPIKE_MAX_TOTAL_DT_S));
    }

    public static Result apply(List<Coordinate> input) {
        if (input == null || input.size() < 3) {
            List<Coordinate> passthrough = input == null ? new ArrayList<>() : new ArrayList<>(input);
            return new Result(passthrough, Collections.emptyList(), 0, 0);
        }

        List<Coordinate> afterSpikes = new ArrayList<>(input.size());
        int spikesRemoved = removeIsolatedSpikes(input, afterSpikes);

        log.info("Isolated-spike cleanup completed: spikesRemoved={} inputSize={} outputSize={}",
                spikesRemoved, input.size(), afterSpikes.size());
        return new Result(afterSpikes, List.of(), spikesRemoved, 0);
    }

    private static int removeIsolatedSpikes(List<Coordinate> input, List<Coordinate> output) {
        int removed = 0;
        output.add(input.get(0));
        for (int i = 1; i < input.size() - 1; i++) {
            Coordinate a = input.get(i - 1);
            Coordinate b = input.get(i);
            Coordinate c = input.get(i + 1);

            double dtAB = deltaSeconds(a, b);
            double dtBC = deltaSeconds(b, c);
            if (Double.isNaN(dtAB) || Double.isNaN(dtBC)
                || dtAB < 0.0 || dtBC < 0.0
                || dtAB > SPIKE_MAX_DT_S || dtBC > SPIKE_MAX_DT_S) {
                output.add(b);
                continue;
            }

            double dAB = GPXReader.getDistanceBetweenTwoWGS84(a, b);
            double dBC = GPXReader.getDistanceBetweenTwoWGS84(b, c);
            double dAC = GPXReader.getDistanceBetweenTwoWGS84(a, c);
            if (dAB <= SPIKE_JUMP_M || dBC <= SPIKE_JUMP_M || dAC >= SPIKE_RETURN_M) {
                output.add(b);
                continue;
            }

            boolean supportedReturn = i + 2 < input.size()
                                      && GPXReader.getDistanceBetweenTwoWGS84(c, input.get(i + 2)) <= SPIKE_SUPPORT_RETURN_M;
            boolean veryShortUnsupportedSpike = dtAB + dtBC <= UNSUPPORTED_SPIKE_MAX_TOTAL_DT_S;
            if (supportedReturn || veryShortUnsupportedSpike) {
                removed++;
                continue;
            }

            output.add(b);
        }
        output.add(input.get(input.size() - 1));
        return removed;
    }

    private static double deltaSeconds(Coordinate a, Coordinate b) {
        if (Double.isNaN(a.getM()) || Double.isNaN(b.getM())) {
            return Double.NaN;
        }
        return b.getM() - a.getM();
    }
}
