package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MediaProcessingWorkerTest {

    private static final long FILE_ID = 7L;

    private final IndexerRepository indexerRepository = mock(IndexerRepository.class);
    private final MediaIndexer mediaIndexer = mock(MediaIndexer.class);
    private final MediaProcessingWorker worker = new MediaProcessingWorker(indexerRepository, mediaIndexer);
    private final IndexedFile indexedFile = new IndexedFile();

    @BeforeEach
    void setUp() {
        indexedFile.setId(FILE_ID);
        indexedFile.setName("synthetic.jpg");
        when(indexerRepository.findById(FILE_ID)).thenReturn(Optional.of(indexedFile));
    }

    @Test
    void indexesNewMediaWithoutDeletingDerivedRows() {
        worker.processCreateOrChange("MEDIA", FILE_ID, false);

        verify(mediaIndexer).indexFile(indexedFile);
        verify(mediaIndexer, never()).refreshFile(indexedFile);
        verify(mediaIndexer, never()).deleteFilesForIndexedFile(indexedFile);
    }

    @Test
    void refreshesMediaInPlaceWhenFileChangesOrReturns() {
        worker.processCreateOrChange("MEDIA", FILE_ID, true);

        verify(mediaIndexer).refreshFile(indexedFile);
        verify(mediaIndexer, never()).deleteFilesForIndexedFile(indexedFile);
        verify(mediaIndexer, never()).indexFile(indexedFile);
    }

    @Test
    void deletesDerivedRowsWhenMediaIsRemoved() {
        worker.processDelete("MEDIA", FILE_ID);

        verify(mediaIndexer).deleteFilesForIndexedFile(indexedFile);
        verify(mediaIndexer, never()).indexFile(indexedFile);
        verify(mediaIndexer, never()).refreshFile(indexedFile);
    }

    @Test
    void ignoresMissingIndexedFile() {
        when(indexerRepository.findById(FILE_ID)).thenReturn(Optional.empty());

        worker.processDelete("MEDIA", FILE_ID);

        verifyNoInteractions(mediaIndexer);
    }
}
