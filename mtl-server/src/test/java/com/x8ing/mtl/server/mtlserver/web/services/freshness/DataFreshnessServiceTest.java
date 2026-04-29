package com.x8ing.mtl.server.mtlserver.web.services.freshness;

import com.x8ing.mtl.server.mtlserver.db.entity.freshness.DataFreshness;
import com.x8ing.mtl.server.mtlserver.db.repository.freshness.DataFreshnessRepository;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataFreshnessServiceTest {

    @Test
    void buildsReadableStableTokenFromSortedRepositoryRows() {
        DataFreshnessRepository repository = mock(DataFreshnessRepository.class);
        when(repository.findAllByOrderByDomainKeyAsc()).thenReturn(List.of(
                row(DataFreshnessDomains.CONFIG, 12, 1000),
                row(DataFreshnessDomains.FILTERS, 28, 3000),
                row(DataFreshnessDomains.INDEX, 91, 2000),
                row(DataFreshnessDomains.MEDIA, 44, 4000),
                row(DataFreshnessDomains.TRACK_GEOMETRY, 133, 5000),
                row(DataFreshnessDomains.TRACKS, 267, 2500)
        ));

        DataFreshnessResponseDto response = new DataFreshnessService(repository).getDataFreshness();

        assertThat(response.freshnessToken()).isEqualTo(
                "5nJmia__|config:12|filters:28|index:91|media:44|track_geometry:133|tracks:267"
        );
        assertThat(response.changedAt()).isEqualTo(new Date(5000));
        assertThat(response.items()).extracting(DataFreshnessItemDto::key).containsExactly(
                DataFreshnessDomains.CONFIG,
                DataFreshnessDomains.FILTERS,
                DataFreshnessDomains.INDEX,
                DataFreshnessDomains.MEDIA,
                DataFreshnessDomains.TRACK_GEOMETRY,
                DataFreshnessDomains.TRACKS
        );
    }

    private static DataFreshness row(String domainKey, long revision, long changedAtMillis) {
        DataFreshness row = new DataFreshness();
        row.setDomainKey(domainKey);
        row.setRevision(revision);
        row.setChangedAt(new Date(changedAtMillis));
        return row;
    }
}
