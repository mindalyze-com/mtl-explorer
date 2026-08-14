package com.x8ing.mtl.server.mtlserver.planner;

import com.x8ing.mtl.server.mtlserver.planner.constants.PlannerConstants;

import java.util.List;

public final class PlannerGeometryMetrics {

    private PlannerGeometryMetrics() {
    }

    public static ElevationTotals elevationTotals(List<double[]> coordinates) {
        double ascent = 0.0;
        double descent = 0.0;
        for (int i = 1; i < coordinates.size(); i++) {
            double delta = coordinates.get(i)[2] - coordinates.get(i - 1)[2];
            if (delta > PlannerConstants.MIN_ELEVATION_DELTA_M) {
                ascent += delta;
            } else if (delta < -PlannerConstants.MIN_ELEVATION_DELTA_M) {
                descent -= delta;
            }
        }
        return new ElevationTotals(ascent, descent);
    }

    public record ElevationTotals(double ascentM, double descentM) {
    }
}
