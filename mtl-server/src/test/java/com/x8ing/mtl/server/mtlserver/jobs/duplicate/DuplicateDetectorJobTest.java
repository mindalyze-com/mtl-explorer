package com.x8ing.mtl.server.mtlserver.jobs.duplicate;

import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DuplicateDetectorJobTest {

    @Test
    void waitsForGpsIndexingToSettle() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        DuplicateDetectorAtomicWorker worker = mock(DuplicateDetectorAtomicWorker.class);
        IndexerStatusService indexerStatusService = mock(IndexerStatusService.class);
        DuplicateDetectorJob job = new DuplicateDetectorJob(repository, worker, indexerStatusService);
        when(indexerStatusService.hasIndexPendingWork(GPXDirectoryWatcherService.INDEX_GPS))
                .thenReturn(true);

        job.run();

        verifyNoInteractions(repository, worker);
    }
}
