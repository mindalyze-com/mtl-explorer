package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackProcessingStatusDto.Stage.*;
import static com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackProcessingStatusDto.State.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TrackProcessingStatusServiceTest {
    private final IndexerRepository indexer = mock(IndexerRepository.class);
    private final GpsTrackRepository tracks = mock(GpsTrackRepository.class);
    private final Clock clock = mock(Clock.class);
    private final Instant start = Instant.parse("2026-10-02T12:00:00Z");
    private final TrackProcessingStatusService service = new TrackProcessingStatusService(indexer, tracks, clock);

    TrackProcessingStatusServiceTest() {
        when(clock.instant()).thenReturn(start);
    }

    @Test
    void startupDoesNotClaimCompleteBeforeDiscoveryRegisters() {
        assertThat(service.getSnapshot().state()).isEqualTo(UNAVAILABLE);
        service.refresh();
        assertThat(service.getSnapshot().stages()).containsExactly(GPS_INDEXING);
    }

    @Test
    void repeatedClicksReadMemoryWithoutProgressQueries() {
        service.registerGpsScanState(() -> false);
        service.refresh();
        clearInvocations(indexer, tracks);
        for (int i = 0; i < 10_000; i++) {
            assertThat(service.getSnapshot().state()).isEqualTo(READY);
        }
        verifyNoInteractions(indexer, tracks);
    }

    @Test
    void reportsOnlyRelevantStagesAndSeparatesFailuresFromPending() {
        service.registerGpsScanState(() -> false);
        when(indexer.countByIndexAndIndexerStatusIn(eq("GPS"), anyCollection())).thenReturn(2L);
        when(indexer.countByIndexAndIndexerStatus("GPS", IndexedFile.IndexerStatus.FAILED)).thenReturn(3L);
        when(tracks.existsByTrackSourceAndDuplicateStatus(GpsTrack.TRACK_SOURCE.IMPORTED,
                GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET)).thenReturn(true);
        when(tracks.countActivityTypePending()).thenReturn(4L);
        service.refresh();
        assertThat(service.getSnapshot().state()).isEqualTo(RUNNING);
        assertThat(service.getSnapshot().stages()).containsExactly(GPS_INDEXING, DUPLICATE_CHECKING, ACTIVITY_CLASSIFICATION);
        assertThat(service.getSnapshot().failedGpsFiles()).isEqualTo(3);
    }

    @Test
    void failedImportsDoNotKeepProcessingRunningForever() {
        service.registerGpsScanState(() -> false);
        when(indexer.countByIndexAndIndexerStatus("GPS", IndexedFile.IndexerStatus.FAILED)).thenReturn(3L);
        service.refresh();
        assertThat(service.getSnapshot().state()).isEqualTo(READY);
        assertThat(service.getSnapshot().failedGpsFiles()).isEqualTo(3);
    }

    @Test
    void discoveryRemainsBusyEvenBeforePendingRowsExist() {
        service.registerGpsScanState(() -> true);
        service.refresh();
        assertThat(service.getSnapshot().state()).isEqualTo(RUNNING);
        assertThat(service.getSnapshot().stages()).containsExactly(GPS_INDEXING);
    }

    @Test
    void newWorkImmediatelyInvalidatesReadyWithoutDatabaseReads() {
        service.registerGpsScanState(() -> false);
        service.refresh();
        clearInvocations(indexer, tracks);
        service.markGpsProcessingStarted();
        assertThat(service.getSnapshot().state()).isEqualTo(RUNNING);
        assertThat(service.getSnapshot().stages()).containsExactly(GPS_INDEXING);
        verifyNoInteractions(indexer, tracks);
        service.refresh();
        assertThat(service.getSnapshot().state()).isEqualTo(READY);
    }

    @Test
    void workArrivingDuringRefreshCannotBeOverwrittenByReady() {
        service.registerGpsScanState(() -> false);
        when(tracks.countActivityTypePending()).thenAnswer(invocation -> {
            service.markGpsProcessingStarted();
            return 0L;
        });
        service.refresh();
        assertThat(service.getSnapshot().stages()).containsExactly(GPS_INDEXING);
        assertThat(service.getSnapshot().state()).isEqualTo(RUNNING);
    }

    @Test
    void failedRefreshAndStaleSnapshotsAreUnavailableAndCanRecover() {
        service.registerGpsScanState(() -> false);
        service.refresh();
        when(clock.instant()).thenReturn(start.plusSeconds(25));
        assertThat(service.getSnapshot().state()).isEqualTo(UNAVAILABLE);
        service.refresh();
        assertThat(service.getSnapshot().state()).isEqualTo(READY);
        when(tracks.countActivityTypePending()).thenThrow(new IllegalStateException("test outage"));
        service.refresh();
        assertThat(service.getSnapshot().state()).isEqualTo(UNAVAILABLE);
        doReturn(0L).when(tracks).countActivityTypePending();
        service.refresh();
        assertThat(service.getSnapshot().state()).isEqualTo(READY);
    }
}
