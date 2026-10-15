package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.SourceFileChangedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MediaProcessingWorkerTest {

    private static final long FILE_ID = 7L;

    @TempDir
    Path sourceDirectory;

    private final IndexerRepository indexerRepository = mock(IndexerRepository.class);
    private final MediaIndexer mediaIndexer = mock(MediaIndexer.class);
    private final MediaProcessingWorker worker = new MediaProcessingWorker(indexerRepository, com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directTransactions(), mock(com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueIngestService.class), mediaIndexer);
    private final IndexedFile indexedFile = new IndexedFile();

    @BeforeEach
    void setUp() throws IOException {
        indexedFile.setId(FILE_ID);
        indexedFile.setName("synthetic.jpg");
        Path source = sourceDirectory.resolve("synthetic.jpg");
        Files.writeString(source, "synthetic");
        indexedFile.setBasePath(sourceDirectory.toString());
        indexedFile.setFullPath(source.toString());
        indexedFile.setSize(Files.size(source));
        indexedFile.setLastModifiedDate(new Date(Files.getLastModifiedTime(source).toMillis()));
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
    void deletesDerivedRowsWhenMediaIsRemoved() throws IOException {
        Files.delete(Path.of(indexedFile.getFullPath()));
        worker.processDelete("MEDIA", FILE_ID);

        verify(mediaIndexer).deleteFilesForIndexedFile(indexedFile);
        verify(mediaIndexer, never()).indexFile(indexedFile);
        verify(mediaIndexer, never()).refreshFile(indexedFile);
    }

    @Test
    void changedSourceRejectsTheImportBeforeTransactionCompletion() {
        doAnswer(invocation -> {
            Files.writeString(Path.of(indexedFile.getFullPath()), "changed while extracting metadata");
            return null;
        }).when(mediaIndexer).refreshFile(indexedFile);
        assertThatThrownBy(() -> worker.processCreateOrChange("MEDIA", FILE_ID, true))
                .isInstanceOf(SourceFileChangedException.class);
    }

    @Test
    void failedExtractionOfAChangingSourceRequestsReplay() {
        doAnswer(invocation -> {
            Files.writeString(Path.of(indexedFile.getFullPath()), "changed while extracting metadata");
            throw new com.x8ing.mtl.server.mtlserver.indexer.SourceFileProblemException("synthetic parse failure", null);
        }).when(mediaIndexer).refreshFile(indexedFile);
        assertThatThrownBy(() -> worker.processCreateOrChange("MEDIA", FILE_ID, true))
                .isInstanceOf(SourceFileChangedException.class);
    }

    @Test
    void restoredSourceCannotBeDeleted() {
        assertThatThrownBy(() -> worker.processDelete("MEDIA", FILE_ID))
                .isInstanceOf(SourceFileChangedException.class);
        verify(mediaIndexer, never()).deleteFilesForIndexedFile(any());
    }

    @Test
    void ignoresMissingIndexedFile() {
        when(indexerRepository.findById(FILE_ID)).thenReturn(Optional.empty());

        worker.processDelete("MEDIA", FILE_ID);

        verifyNoInteractions(mediaIndexer);
    }
}
