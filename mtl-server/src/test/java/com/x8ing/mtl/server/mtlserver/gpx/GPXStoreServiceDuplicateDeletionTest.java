package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.simplified.SimplifiedTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.logic.motion.GpsTrackEventService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GPXStoreServiceDuplicateDeletionTest {
    private final GpsTrackRepository tracks = mock(GpsTrackRepository.class);
    private final GPXStoreService store = new GPXStoreService(
            tracks, mock(GpsTrackDataRepository.class), mock(SimplifiedTrackRepository.class),
            mock(GpsTrackDataPointRepository.class), mock(GpsTrackEventRepository.class),
            mock(GpsTrackEventService.class), Map.of("kalman", mock(GpsSmoothingAlgorithm.class)), "kalman");

    @Test
    void deletingPrimaryReleasesEveryDirectDuplicateForRechecking() {
        GpsTrack primary = track(101L, null);
        when(tracks.findByIdForUpdate(101L)).thenReturn(Optional.of(primary));
        when(tracks.releaseDuplicateReferences(101L)).thenReturn(2);

        store.deleteWithAllDependencies(primary);

        verify(tracks).releaseDuplicateReferences(101L);
        verify(tracks).clearSegmentParentReferences(101L);
        verify(tracks, never()).save(any());
        verify(tracks).deleteById(101L);
    }

    @Test
    void deletingDuplicatePreservesItsPrimaryAndSibling() {
        GpsTrack primary = track(101L, null);
        GpsTrack duplicate = track(102L, 101L);
        GpsTrack sibling = track(103L, 101L);
        when(tracks.findByIdForUpdate(102L)).thenReturn(Optional.of(duplicate));
        when(tracks.getDuplicatesForGpsTrackId(102L)).thenReturn(List.of(101L, 103L));
        when(tracks.findById(101L)).thenReturn(Optional.of(primary));
        when(tracks.findById(103L)).thenReturn(Optional.of(sibling));

        store.deleteWithAllDependencies(duplicate);

        assertThat(primary.getDuplicateStatus()).isEqualTo(GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE);
        assertThat(sibling.getDuplicateOf()).isEqualTo(101L);
        assertThat(sibling.getDuplicateStatus()).isEqualTo(GpsTrack.DUPLICATE_CHECK_STATUS.DUPLICATE);
        verify(tracks).releaseDuplicateReferences(102L);
        verify(tracks).clearSegmentParentReferences(102L);
        verify(tracks, never()).save(any());
        verify(tracks).deleteById(102L);
    }

    private static GpsTrack track(long id, Long primaryId) {
        IndexedFile file = new IndexedFile();
        file.setPath("/synthetic");
        file.setName("duplicate-test-" + id + ".gpx");
        GpsTrack track = new GpsTrack();
        track.setId(id);
        track.setIndexedFile(file);
        track.setDuplicateOf(primaryId);
        track.setDuplicateStatus(primaryId == null ? GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE : GpsTrack.DUPLICATE_CHECK_STATUS.DUPLICATE);
        return track;
    }
}
