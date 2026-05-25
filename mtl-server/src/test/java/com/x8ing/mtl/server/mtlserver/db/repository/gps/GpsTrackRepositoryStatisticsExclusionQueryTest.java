package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

class GpsTrackRepositoryStatisticsExclusionQueryTest {

    @Test
    void highlightRankingsAndMilestonesOmitHighlightAndStatisticsExclusions() throws NoSuchMethodException {
        assertThat(query("getTrackOverviewTrackRankings", Long[].class, int.class))
                .contains("highlight_exclusion_reason IS NULL")
                .contains("statistics_exclusion_reason IS NULL");

        assertThat(query("getTrackOverviewMilestones", Long[].class))
                .contains("highlight_exclusion_reason IS NULL")
                .contains("statistics_exclusion_reason IS NULL");
    }

    @Test
    void summaryAndPeriodStatisticsOnlyOmitStatisticsExclusions() throws NoSuchMethodException {
        assertStatisticsOnlyExclusion(query("getTrackOverviewSummary", Long[].class));
        assertStatisticsOnlyExclusion(query("getTrackOverviewActivityBreakdown", Long[].class));
        assertStatisticsOnlyExclusion(query("getTrackOverviewPeriodDistributions", Long[].class, int.class));

        assertThat(query("getTrackStatistics", String.class, String.class, Long[].class, Double.class))
                .contains("gt.statistics_exclusion_reason IS NULL")
                .doesNotContain("NOT EXISTS")
                .doesNotContain("highlight_exclusion_reason");
    }

    @Test
    void overviewExclusionSummaryCountsHighlightAndStatisticsScopes() throws NoSuchMethodException {
        assertThat(query("getTrackOverviewExclusions", Long[].class))
                .contains("highlight_exclusion_reason IS NOT NULL")
                .contains("statistics_exclusion_reason IS NOT NULL")
                .contains("highlightExcludedTrackCount")
                .contains("statisticsExcludedTrackCount");
    }

    private static void assertStatisticsOnlyExclusion(String query) {
        assertThat(query)
                .contains("statistics_exclusion_reason IS NULL")
                .doesNotContain("highlight_exclusion_reason");
    }

    private static String query(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Query annotation = GpsTrackRepository.class.getMethod(methodName, parameterTypes).getAnnotation(Query.class);
        assertThat(annotation).isNotNull();
        return annotation.value().replaceAll("\\s+", " ");
    }
}
