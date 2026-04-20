package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class TracksSimplifiedResponse implements VersionAware {

    /**
     * How many tracks the base filter (same filterName, no user params) exposes.
     */
    private Long standardFilterCount;

    /**
     * Convenience: number of tracks actually returned after applying user filter params.
     */
    private int numberOfFilteredMatchedTracks;

    /**
     * The filtered track list — present in mode=full (default), null in mode=ids.
     */
    private List<GpsTrackResponse> filteredTracks;

    /**
     * In mode=ids: map of trackId → entity version (@Version).
     * The client compares each version against its cached copy to detect stale tracks. Null in mode=full.
     */
    private Map<Long, Long> trackVersions;

    /**
     * In mode=ids: filter group assignment per track. Null in mode=full.
     * Key: trackId, Value: group name.
     */
    private Map<Long, String> filterGroups;

    /**
     * Constructor for mode=full (backward compatible)
     */
    public TracksSimplifiedResponse(long standardFilterCount, int numberOfFilteredMatchedTracks, List<GpsTrackResponse> filteredTracks) {
        this((Long) standardFilterCount, numberOfFilteredMatchedTracks, filteredTracks, null, null);
    }

    /**
     * Constructor for mode=ids
     */
    public static TracksSimplifiedResponse idsOnly(long standardFilterCount, int filteredCount, Map<Long, Long> trackVersions, Map<Long, String> filterGroups) {
        return new TracksSimplifiedResponse((Long) standardFilterCount, filteredCount, null, trackVersions, filterGroups);
    }
}
