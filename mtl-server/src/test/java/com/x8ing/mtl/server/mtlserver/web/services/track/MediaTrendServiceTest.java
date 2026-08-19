package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaTrendQueryRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.media.TrackMediaPageResult;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendBucketDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendGrouping;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendItemsRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendKindFilter;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendScope;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MediaTrendServiceTest {

    private final MediaTrendQueryRepository repository = mock(MediaTrendQueryRepository.class);
    private final MediaTrendService service = new MediaTrendService(repository);

    @Test
    void returnsMatchedTrendBucketsAndDeduplicatesTrackIds() {
        var bucket = new MediaTrendBucketDto("2026-Q3", "2026-Q3", "Q3", false, 4, 1);
        when(repository.findTrendBuckets(
                MediaTrendGrouping.QUARTER,
                MediaTrendScope.MATCHED_ACTIVITIES,
                List.of(7L, 9L))).thenReturn(List.of(bucket));

        var response = service.getTrends(new MediaTrendRequest(
                MediaTrendGrouping.QUARTER,
                MediaTrendScope.MATCHED_ACTIVITIES,
                List.of(7L, 9L, 7L)));

        assertThat(response.scope()).isEqualTo(MediaTrendScope.MATCHED_ACTIVITIES);
        assertThat(response.buckets()).containsExactly(bucket);
    }

    @Test
    void allIndexedScopeDoesNotForwardTrackIds() {
        when(repository.findTrendBuckets(
                MediaTrendGrouping.YEAR,
                MediaTrendScope.ALL_INDEXED,
                List.of())).thenReturn(List.of());

        service.getTrends(new MediaTrendRequest(
                MediaTrendGrouping.YEAR,
                MediaTrendScope.ALL_INDEXED,
                List.of(7L)));

        verify(repository).findTrendBuckets(
                MediaTrendGrouping.YEAR,
                MediaTrendScope.ALL_INDEXED,
                List.of());
    }

    @Test
    void validatesMatchedScopeAndBucketKeys() {
        assertBadRequest(() -> service.getTrends(new MediaTrendRequest(
                MediaTrendGrouping.YEAR,
                MediaTrendScope.MATCHED_ACTIVITIES,
                null)));
        assertBadRequest(() -> service.getItems(new MediaTrendItemsRequest(
                MediaTrendGrouping.MONTH,
                MediaTrendScope.ALL_INDEXED,
                "2026-19",
                MediaTrendKindFilter.ALL,
                null,
                0,
                60)));
        assertBadRequest(() -> service.getItems(new MediaTrendItemsRequest(
                MediaTrendGrouping.YEAR,
                MediaTrendScope.MATCHED_ACTIVITIES,
                MediaTrendGrouping.UNDATED_BUCKET_KEY,
                MediaTrendKindFilter.ALL,
                List.of(7L),
                0,
                60)));

        verifyNoInteractions(repository);
    }

    @Test
    void defaultsPageSettingsAndCalculatesPageCount() {
        when(repository.findItems(
                MediaTrendGrouping.DAY,
                MediaTrendScope.ALL_INDEXED,
                "2026-08-17",
                MediaTrendKindFilter.ALL,
                List.of(),
                MediaTrendService.DEFAULT_PAGE_SIZE,
                0)).thenReturn(new TrackMediaPageResult<>(List.of(), 121));

        var page = service.getItems(new MediaTrendItemsRequest(
                MediaTrendGrouping.DAY,
                MediaTrendScope.ALL_INDEXED,
                "2026-08-17",
                null,
                null,
                null,
                null));

        assertThat(page.page()).isZero();
        assertThat(page.pageSize()).isEqualTo(MediaTrendService.DEFAULT_PAGE_SIZE);
        assertThat(page.totalItems()).isEqualTo(121);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void rejectsInvalidPagination() {
        assertBadRequest(() -> service.getItems(new MediaTrendItemsRequest(
                MediaTrendGrouping.TOTAL,
                MediaTrendScope.ALL_INDEXED,
                MediaTrendGrouping.TOTAL_BUCKET_KEY,
                MediaTrendKindFilter.IMAGE,
                null,
                -1,
                MediaTrendService.MAX_PAGE_SIZE + 1)));

        verifyNoInteractions(repository);
    }

    private static void assertBadRequest(Runnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
