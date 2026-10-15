package com.x8ing.mtl.server.mtlserver.googlehealth.application;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorSummaryRow;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GoogleHealthMirrorCacheTest {
    @Test
    void cataloguesRefreshDuringLongImportsWhileTotalsRetainTheirSnapshotTime() {
        MirrorRepository repository = mock(MirrorRepository.class);
        SyncCoordinator coordinator = mock(SyncCoordinator.class);
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2042-08-02T12:00:00Z");
        when(clock.instant()).thenReturn(start);
        when(coordinator.hasActiveJob()).thenReturn(true);
        when(coordinator.status()).thenReturn(SyncCoordinator.JobSnapshot.idle());
        when(repository.findMirrorSummary()).thenReturn(MirrorSummaryRow.empty());
        GoogleHealthMirrorCache cache = new GoogleHealthMirrorCache(repository, coordinator, clock);
        cache.mirrorSummary(coordinator.status());
        cache.typeSummaries();
        cache.dataSources();
        when(clock.instant()).thenReturn(start.plusSeconds(30));
        cache.typeSummaries();
        cache.dataSources();
        verify(repository, times(1)).findTypeSummaries();
        verify(repository, times(1)).findDataSourceSummaries();
        when(clock.instant()).thenReturn(start.plusSeconds(61));
        cache.mirrorSummary(coordinator.status());
        cache.typeSummaries();
        cache.dataSources();
        verify(repository, times(2)).findTypeSummaries();
        verify(repository, times(2)).findDataSourceSummaries();
        verify(repository, times(1)).findMirrorSummary();
        assertThat(cache.mirrorSnapshotAt()).isEqualTo(start);
    }
}
