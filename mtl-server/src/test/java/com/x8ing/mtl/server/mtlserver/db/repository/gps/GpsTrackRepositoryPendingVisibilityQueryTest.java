package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

class GpsTrackRepositoryPendingVisibilityQueryTest {

    @Test
    void uiFacingQueriesIncludeSuccessfulImportedTracksWaitingForDuplicateDetection()
            throws NoSuchMethodException {
        assertVisibleStatuses(query(
                "findImportedTrackBounds",
                double.class,
                double.class,
                double.class,
                double.class));
        assertVisibleStatuses(query(
                "getTrackStatistics",
                String.class,
                String.class,
                Long[].class,
                Double.class));
        assertVisibleStatuses(query("getRelatedTrackIdsNext", Long.class, Long[].class));
        assertVisibleStatuses(query("getRelatedTrackIdsPrevious", Long.class, Long[].class));
    }

    @Test
    void demoTrackCountingRemainsRestrictedToConfirmedUniqueTracks() throws NoSuchMethodException {
        assertThat(query("countGoodTracks"))
                .contains("DUPLICATE_CHECK_STATUS.UNIQUE")
                .doesNotContain("NOT_CHECKED_YET");
    }

    private static void assertVisibleStatuses(String query) {
        assertThat(query)
                .contains("UNIQUE")
                .contains("NOT_CHECKED_YET")
                .contains("SUCCESS")
                .contains("IMPORTED")
                .doesNotContain("DUPLICATE_CHECK_STATUS.DUPLICATE")
                .doesNotContain("DUPLICATE_CHECK_STATUS.EXCLUDED")
                .doesNotContain("'DUPLICATE'")
                .doesNotContain("'EXCLUDED'");
    }

    private static String query(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Query annotation = GpsTrackRepository.class.getMethod(methodName, parameterTypes).getAnnotation(Query.class);
        assertThat(annotation).isNotNull();
        return annotation.value().replaceAll("\\s+", " ");
    }
}
