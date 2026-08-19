package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.utils.TimingCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GpxProcessingWorkerTest {

    private static final long FILE_ID = 7L;

    private final IndexerRepository indexerRepository = mock(IndexerRepository.class);
    private final GPXStoreService gpsStoreService = mock(GPXStoreService.class);
    private final TrackFileConverterService converterService = mock(TrackFileConverterService.class);
    private final GpxProcessingWorker worker = new GpxProcessingWorker(
            indexerRepository,
            gpsStoreService,
            converterService);

    @BeforeEach
    void setUp() {
        IndexedFile file = new IndexedFile();
        file.setId(FILE_ID);
        file.setName("synthetic.gpx");
        file.setPath("");
        file.setFullPath("/tmp/synthetic.gpx");
        when(indexerRepository.findById(FILE_ID)).thenReturn(Optional.of(file));
    }

    @Test
    void failedGpsLoadIsReportedToTheFileIndexer() {
        when(gpsStoreService.readAndSave(any(), eq(null), any(TimingCollector.class)))
                .thenReturn(List.of(loadResult(GpsTrack.LOAD_STATUS.FAILED)));

        assertThatThrownBy(() -> worker.processCreateOrChange("GPS", FILE_ID, false))
                .isInstanceOf(GpxProcessingWorker.GpxImportFailedException.class)
                .hasMessage("GPS import finished with status FAILED");
    }

    @Test
    void missingLoadResultIsReportedToTheFileIndexer() {
        when(gpsStoreService.readAndSave(any(), eq(null), any(TimingCollector.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> worker.processCreateOrChange("GPS", FILE_ID, false))
                .isInstanceOf(GpxProcessingWorker.GpxImportFailedException.class)
                .hasMessage("GPS import produced no result");
    }

    @Test
    void successfulAndEmptyGpsLoadsCompleteNormally() {
        when(gpsStoreService.readAndSave(any(), eq(null), any(TimingCollector.class)))
                .thenReturn(List.of(
                        loadResult(GpsTrack.LOAD_STATUS.SUCCESS),
                        loadResult(GpsTrack.LOAD_STATUS.EMPTY_FILE)));

        assertThatCode(() -> worker.processCreateOrChange("GPS", FILE_ID, false))
                .doesNotThrowAnyException();
    }

    @Test
    void changedPathDeletesExistingTracksBeforeReimport() {
        when(gpsStoreService.readAndSave(any(), eq(null), any(TimingCollector.class)))
                .thenReturn(List.of(loadResult(GpsTrack.LOAD_STATUS.SUCCESS)));

        worker.processCreateOrChange("GPS", FILE_ID, true);

        var inOrder = inOrder(gpsStoreService);
        inOrder.verify(gpsStoreService).deleteTracksForFile(any(IndexedFile.class));
        inOrder.verify(gpsStoreService).readAndSave(any(), eq(null), any(TimingCollector.class));
    }

    @Test
    void failedTrackRowIsCommittedBeforeFailureReachesTheFileIndexer() throws Exception {
        Method method = GpxProcessingWorker.class.getMethod(
                "processCreateOrChange", String.class, long.class, boolean.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.noRollbackFor())
                .containsExactly(GpxProcessingWorker.GpxImportFailedException.class);
    }

    private static GPXReader.LoadResult loadResult(GpsTrack.LOAD_STATUS status) {
        GpsTrack track = new GpsTrack();
        track.setLoadStatus(status);
        GPXReader.LoadResult result = new GPXReader.LoadResult();
        result.gpsTrack = track;
        return result;
    }
}
