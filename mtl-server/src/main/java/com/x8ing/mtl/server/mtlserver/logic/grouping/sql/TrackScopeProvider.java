package com.x8ing.mtl.server.mtlserver.logic.grouping.sql;

import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.TrackViewScope;

import java.util.Set;

/**
 * Supplies the canonical track IDs for an optional track scope.
 */
public interface TrackScopeProvider {
    TrackViewScope scope();

    Set<Long> trackIds();
}
