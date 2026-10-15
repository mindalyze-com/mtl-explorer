package com.x8ing.mtl.server.mtlserver.planner;

import com.x8ing.mtl.server.mtlserver.gpx.BRouterElevationCalculator;
import com.x8ing.mtl.server.mtlserver.planner.constants.PlannerConstants;
import com.x8ing.mtl.server.mtlserver.planner.dto.LegResultDto;
import com.x8ing.mtl.server.mtlserver.planner.dto.LiveStatsDto;

import java.util.ArrayList;
import java.util.List;

public final class PlannerGeometryMetrics {

    private PlannerGeometryMetrics() {
    }

    public static ElevationTotals elevationTotals(List<double[]> coordinates) {
        double[] elevations = new double[coordinates.size()];
        for (int i = 0; i < coordinates.size(); i++) {
            double[] coordinate = coordinates.get(i);
            elevations[i] = coordinate != null && coordinate.length >= 3 ? coordinate[2] : Double.NaN;
        }
        var totals = BRouterElevationCalculator.calculate(elevations, PlannerConstants.ELEVATION_FILTER_BUFFER_M);
        return new ElevationTotals(totals.ascentM(), totals.descentM());
    }

    public static List<double[]> coordinates(List<LegResultDto> legs) {
        List<double[]> coordinates = new ArrayList<>();
        for (LegResultDto leg : legs) coordinates.addAll(leg.getCoordinates());
        return coordinates;
    }

    /**
     * Filter across the whole route: per-leg filtered elevation totals are not additive.
     */
    public static LiveStatsDto routeStats(List<LegResultDto> legs) {
        LiveStatsDto stats = new LiveStatsDto();
        for (LegResultDto leg : legs) {
            stats.setDistanceM(stats.getDistanceM() + leg.getDistanceM());
            stats.setDurationSec(stats.getDurationSec() + leg.getDurationSec());
            if (leg.isCached()) stats.setAnyLegCached(true);
        }
        stats.setLegCount(legs.size());
        updateElevation(stats, coordinates(legs));
        return stats;
    }

    public static void updateElevation(LiveStatsDto stats, List<double[]> coordinates) {
        ElevationTotals totals = elevationTotals(coordinates);
        stats.setAscentM(totals.ascentM());
        stats.setDescentM(totals.descentM());
    }

    public record ElevationTotals(double ascentM, double descentM) {
    }
}
