package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;

/**
 * Route work admitted by the synchronization worker.
 */
@FunctionalInterface
public interface ExerciseRouteWork {
    RouteRefreshResult retryDueRoutes(SyncCoordinator.Progress progress);

    record RouteRefreshResult(int published, int noRoute, int failed) {
        public static RouteRefreshResult empty() {
            return new RouteRefreshResult(0, 0, 0);
        }
    }
}
