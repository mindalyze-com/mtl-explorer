package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"startDate", "endDate", "timeZone", "requestedGrouping", "resolvedGrouping", "series"})
public record GHGroupedMetrics(
        String startDate, String endDate, String timeZone,
        GHGrouping requestedGrouping, GHGrouping resolvedGrouping, List<GHGroupedSeries> series) {
}
