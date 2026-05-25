package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Date;
import java.util.List;

@JsonPropertyOrder({
        "summary",
        "activityBreakdown",
        "trackRankings",
        "recentActivities",
        "activePeriods",
        "periodDistributions",
        "milestones",
        "exclusionSummary"
})
public record StatisticsOverviewResponseDto(
        Summary summary,
        List<ActivityBreakdown> activityBreakdown,
        List<TrackRanking> trackRankings,
        List<TrackRef> recentActivities,
        List<PeriodRow> activePeriods,
        List<PeriodDistribution> periodDistributions,
        List<TrackRef> milestones,
        ExclusionSummary exclusionSummary
) {

    @JsonPropertyOrder({
            "trackCount",
            "distanceM",
            "durationMs",
            "energyWh",
            "oldestStart",
            "newestStart"
    })
    public record Summary(
            long trackCount,
            double distanceM,
            double durationMs,
            double energyWh,
            Date oldestStart,
            Date newestStart
    ) {
    }

    @JsonPropertyOrder({
            "activityType",
            "trackCount",
            "distanceM",
            "durationMs",
            "energyWh"
    })
    public record ActivityBreakdown(
            String activityType,
            long trackCount,
            double distanceM,
            double durationMs,
            double energyWh
    ) {
    }

    @JsonPropertyOrder({
            "rowKey",
            "rows"
    })
    public record TrackRanking(
            String rowKey,
            List<TrackRef> rows
    ) {
    }

    @JsonPropertyOrder({
            "sortOrder",
            "rowKey",
            "trackId",
            "value"
    })
    public record TrackRef(
            Integer sortOrder,
            String rowKey,
            Long trackId,
            double value
    ) {
    }

    @JsonPropertyOrder({
            "sortOrder",
            "periodType",
            "periodKey",
            "label",
            "trackCount",
            "distanceM",
            "durationMs"
    })
    public record PeriodRow(
            Integer sortOrder,
            String periodType,
            String periodKey,
            String label,
            long trackCount,
            double distanceM,
            double durationMs
    ) {
    }

    @JsonPropertyOrder({
            "periodType",
            "rows"
    })
    public record PeriodDistribution(
            String periodType,
            List<PeriodRow> rows
    ) {
    }

    @JsonPropertyOrder({
            "highlightExcludedTrackCount",
            "statisticsExcludedTrackCount"
    })
    public record ExclusionSummary(
            long highlightExcludedTrackCount,
            long statisticsExcludedTrackCount
    ) {
    }
}
