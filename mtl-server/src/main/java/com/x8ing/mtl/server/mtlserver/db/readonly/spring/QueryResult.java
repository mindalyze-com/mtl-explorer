package com.x8ing.mtl.server.mtlserver.db.readonly.spring;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.filter.FilterResultGroupSummary;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VersionAware;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@JsonPropertyOrder({
        "resultEntries",
        "groupingAvailable",
        "availableGroups",
        "preGroupSelectionCount",
        "trackVersions",
        "standardFilterCount",
        "filterGroups"
})
public class QueryResult implements VersionAware {


    @Data
    @JsonPropertyOrder({
            "id",
            "group",
            "gpsTrack"
    })
    public static class QueryResultEntry {

        private Long id;

        // optional group
        private String group;

        // optional, only filled if requested
        private GpsTrack gpsTrack;
    }

    private List<QueryResultEntry> resultEntries = new ArrayList<>();

    /** True when the dynamic SQL result includes a grp column, even if every value is null. */
    private boolean groupingAvailable;

    /** Group counts before applying the optional exact result-group selection. */
    private List<FilterResultGroupSummary> availableGroups = new ArrayList<>();

    /** Number of rows returned by the dynamic SQL before result-group selection. */
    private long preGroupSelectionCount;

    /**
     * Track entity versions (id → version). Populated by filter/resolve for map cache invalidation.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<Long, Long> trackVersions;

    /**
     * Total number of tracks in the system (unfiltered). Populated by filter/resolve.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long standardFilterCount;

    /**
     * Filter group assignment per track (trackId → group name). Populated by filter/resolve.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<Long, String> filterGroups;

    public Long[] asIdArray() {
        if (resultEntries == null || resultEntries.isEmpty()) {
            return new Long[0];
        }

        return resultEntries.stream()
                .map(QueryResultEntry::getId)
                .toArray(Long[]::new);
    }

    public List<Long> asIdList() {
        if (resultEntries == null || resultEntries.isEmpty()) {
            return List.of();
        }

        return resultEntries.stream()
                .map(QueryResultEntry::getId)
                .toList();
    }

}
