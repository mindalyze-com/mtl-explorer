package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"dataType", "title", "category", "unit", "decimals", "family", "grouping",
        "detailedHeartRate", "buckets", "detailedPoints"})
public record GHGroupedSeries(
        String dataType, String title, GHMetricSeries.Category category, String unit, int decimals,
        Family family, GHGrouping grouping, boolean detailedHeartRate,
        List<GHMetricBucket> buckets, List<GHMetricPoint> detailedPoints) {
    public enum Family {ACTIVITY, SLEEP, MEASUREMENT}
}
