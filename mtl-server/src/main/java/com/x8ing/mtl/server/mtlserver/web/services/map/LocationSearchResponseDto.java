package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonPropertyOrder({
        "query",
        "normalizedQuery",
        "limit",
        "sort",
        "ready",
        "phase",
        "message",
        "results"
})
public class LocationSearchResponseDto {

    private String query;

    @JsonProperty("normalized_query")
    private String normalizedQuery;

    private int limit;

    private String sort;

    private boolean ready;

    private String phase;

    private String message;

    private List<LocationSearchResultDto> results = new ArrayList<>();

    public static LocationSearchResponseDto unavailable(String query, int limit, String sort, String phase, String message) {
        LocationSearchResponseDto dto = new LocationSearchResponseDto();
        dto.setQuery(query);
        dto.setLimit(limit);
        dto.setSort(sort);
        dto.setReady(false);
        dto.setPhase(phase);
        dto.setMessage(message);
        return dto;
    }
}
