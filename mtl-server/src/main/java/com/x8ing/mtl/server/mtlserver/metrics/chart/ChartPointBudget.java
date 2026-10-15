package com.x8ing.mtl.server.mtlserver.metrics.chart;

/**
 * Shared response budget; coverage boundaries and exact endpoints may add display points.
 */
public final class ChartPointBudget {
    public static final int DEFAULT = 350;
    public static final int MAX = 3_000;
    private static final int MIN = 1;

    private ChartPointBudget() {
    }

    public static int resolve(Integer requested) {
        return requested == null ? DEFAULT : Math.clamp(requested, MIN, MAX);
    }
}
