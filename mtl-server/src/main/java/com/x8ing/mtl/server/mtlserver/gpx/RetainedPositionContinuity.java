package com.x8ing.mtl.server.mtlserver.gpx;

import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Disconnects abrupt relocations supported by surrounding observations. Retains every position.
 */
final class RetainedPositionContinuity {
    static final String RULE = "contextual-position-discontinuity";
    private static final int CONTEXT_INTERVALS = 4;
    private static final int MIN_CONTEXT_INTERVALS_PER_SIDE = 2;
    private static final int MIN_EDGE_CONTEXT_INTERVALS = 3;
    private static final double MIN_JUMP_METRES = 200;
    private static final double MIN_JUMP_SPEED_METRES_PER_SECOND = 50;
    private static final double MIN_EDGE_JUMP_SPEED_METRES_PER_SECOND = 125;
    private static final double CONTEXT_SPEED_RATIO = 6;

    private RetainedPositionContinuity() {
    }

    static Map<String, String> settings() {
        return Map.of("position.continuityContextIntervals", Integer.toString(CONTEXT_INTERVALS),
                "position.continuityMinimumContextPerSide", Integer.toString(MIN_CONTEXT_INTERVALS_PER_SIDE),
                "position.continuityMinimumEdgeContext", Integer.toString(MIN_EDGE_CONTEXT_INTERVALS),
                "position.continuityMinimumDistanceMetres", Double.toString(MIN_JUMP_METRES),
                "position.continuityMinimumSpeedMetresPerSecond", Double.toString(MIN_JUMP_SPEED_METRES_PER_SECOND),
                "position.continuityMinimumEdgeSpeedMetresPerSecond", Double.toString(MIN_EDGE_JUMP_SPEED_METRES_PER_SECOND),
                "position.continuitySpeedRatio", Double.toString(CONTEXT_SPEED_RATIO));
    }

    static List<Integer> breaks(List<Coordinate> points) {
        double[] speeds = new double[points.size()];
        Arrays.fill(speeds, Double.NaN);
        for (int i = 1; i < points.size(); i++) {
            double dt = points.get(i).getM() - points.get(i - 1).getM();
            if (Double.isFinite(dt) && dt > 0 && dt <= RecordingContinuity.MAX_INTERPOLATION_GAP_SECONDS)
                speeds[i] = GPXReader.getDistanceBetweenTwoWGS84(points.get(i - 1), points.get(i)) / dt;
        }
        List<Integer> result = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            if (!(speeds[i] >= MIN_JUMP_SPEED_METRES_PER_SECOND)
                || GPXReader.getDistanceBetweenTwoWGS84(points.get(i - 1), points.get(i)) < MIN_JUMP_METRES) continue;
            int beforeFrom = Math.max(1, i - CONTEXT_INTERVALS), afterTo = Math.min(speeds.length, i + CONTEXT_INTERVALS + 1);
            double before = contextSpeed(speeds, beforeFrom, i, MIN_CONTEXT_INTERVALS_PER_SIDE);
            double after = contextSpeed(speeds, i + 1, afterTo, MIN_CONTEXT_INTERVALS_PER_SIDE);
            if (Double.isFinite(before) && Double.isFinite(after)
                && speeds[i] > CONTEXT_SPEED_RATIO * Math.max(before, after)) result.add(i);
            else if ((!Double.isFinite(before) || !Double.isFinite(after)) && speeds[i] >= MIN_EDGE_JUMP_SPEED_METRES_PER_SECOND) {
                // A boundary has less evidence. Require a much faster leap and more support on the available side.
                before = contextSpeed(speeds, beforeFrom, i, MIN_EDGE_CONTEXT_INTERVALS);
                after = contextSpeed(speeds, i + 1, afterTo, MIN_EDGE_CONTEXT_INTERVALS);
                double supported = Double.isFinite(before) ? before : after;
                if (Double.isFinite(supported) && speeds[i] > CONTEXT_SPEED_RATIO * supported) result.add(i);
            }
        }
        return List.copyOf(result);
    }

    private static double contextSpeed(double[] speeds, int from, int to, int minimumIntervals) {
        double[] usable = Arrays.stream(speeds, from, to).filter(Double::isFinite).sorted().toArray();
        if (usable.length < minimumIntervals) return Double.NaN;
        return usable.length % 2 == 0 ? (usable[usable.length / 2 - 1] + usable[usable.length / 2]) / 2
                : usable[usable.length / 2];
    }
}
