package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import java.util.Map;

/**
 * Any response that carries track entity versions for client-side cache
 * invalidation.  The client compares returned versions against its local
 * IndexedDB cache and selectively re-fetches stale entries.
 * <p>
 * This is intentionally <b>not</b> coupled to filter concerns (group
 * assignments, total counts) — those are filter-specific context that only
 * some responses carry.
 * <p>
 * Implementors:
 * <ul>
 *   <li>{@link TracksSimplifiedResponse} (get-simplified, mode=ids)</li>
 *   <li>{@link com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult} (filter/resolve)</li>
 * </ul>
 */
public interface VersionAware {

    /**
     * Track ID → entity version (@Version).
     */
    Map<Long, Long> getTrackVersions();

    void setTrackVersions(Map<Long, Long> trackVersions);
}
