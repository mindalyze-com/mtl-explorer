package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({
        "dataType",
        "title",
        "category",
        "unit",
        "chartType",
        "decimals",
        "points"
})
public record GHMetricSeries(
        String dataType,
        String title,
        Category category,
        String unit,
        ChartType chartType,
        int decimals,
        List<GHMetricPoint> points
) {
    public enum Category {
        ACTIVITY,
        HEART_AND_VITALS,
        SLEEP,
        BODY
    }

    public enum ChartType {
        COLUMN,
        LINE,
        RANGE
    }
}
