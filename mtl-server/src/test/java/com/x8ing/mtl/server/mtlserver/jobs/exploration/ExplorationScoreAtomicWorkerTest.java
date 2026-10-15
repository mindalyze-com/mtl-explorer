package com.x8ing.mtl.server.mtlserver.jobs.exploration;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExplorationScoreAtomicWorkerTest {
    private final GpsTrackRepository tracks = mock(GpsTrackRepository.class);
    private final ExplorationScoreQueryRepository queries = mock(ExplorationScoreQueryRepository.class);
    private final ExplorationScoreAtomicWorker worker = new ExplorationScoreAtomicWorker(tracks, queries);

    @Test
    void storesRoundedResultWithoutChangingTheLoadedTrack() {
        var track = input();
        when(queries.calculateExplorationScore(eq(track.getId()), any(), anyDouble(), anyInt()))
                .thenReturn(new ExplorationScoreQueryRepository.ExplorationResult(100, 74.56, 0.7456));

        assertThat(worker.processOne(track.getId())).isTrue();

        verify(tracks).updateExplorationResult(eq(track.getId()), eq(GpsTrack.EXPLORATION_STATUS.CALCULATED),
                eq(0.746), any(Date.class));
        assertInputUnchanged(track);
    }

    @Test
    void unavailableGeometryClearsOnlyExplorationResults() {
        var track = input();

        assertThat(worker.processOne(track.getId())).isTrue();

        verify(tracks).updateExplorationResult(eq(track.getId()), eq(GpsTrack.EXPLORATION_STATUS.NOT_SCHEDULED),
                isNull(), any(Date.class));
        assertInputUnchanged(track);
    }

    @Test
    void calculationFailurePreservesTheLastScoreAndMarksOnlyExplorationForRetry() {
        var track = input();
        when(queries.calculateExplorationScore(eq(track.getId()), any(), anyDouble(), anyInt()))
                .thenThrow(new IllegalStateException("synthetic calculation failure"));

        assertThat(worker.processOne(track.getId())).isFalse();

        verify(tracks).markExplorationForRetry(eq(track.getId()), any(Date.class));
        verify(tracks, never()).updateExplorationResult(any(), any(), any(), any());
        assertInputUnchanged(track);
    }

    private GpsTrack input() {
        var track = new GpsTrack();
        track.setId(42L);
        track.setStartDate(new Date(0));
        track.setExplorationStatus(GpsTrack.EXPLORATION_STATUS.NEEDS_RECALCULATION);
        track.setExplorationScore(0.2);
        when(tracks.findById(track.getId())).thenReturn(Optional.of(track));
        return track;
    }

    private void assertInputUnchanged(GpsTrack track) {
        assertThat(track.getExplorationStatus()).isEqualTo(GpsTrack.EXPLORATION_STATUS.NEEDS_RECALCULATION);
        assertThat(track.getExplorationScore()).isEqualTo(0.2);
        assertThat(track.getExplorationCalcDate()).isNull();
        verify(tracks, never()).save(any());
    }
}
