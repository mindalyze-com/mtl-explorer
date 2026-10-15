package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.event.FileIndexerObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FileIndexerShutdownTest {
    private static final String INDEX = "GPS";
    private static final int AWAIT_SECONDS = 5;

    @Test
    void cancelledDirectoryWalkDoesNotTreatUnvisitedFilesAsMissing(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("first.gpx"), "synthetic");
        Files.writeString(directory.resolve("second.gpx"), "synthetic");
        var repository = mock(IndexerRepository.class);
        var observer = mock(FileIndexerObserver.class);
        var existing = new IndexedFile();
        existing.setPath("");
        existing.setName("second.gpx");
        when(repository.findByIndex(INDEX)).thenReturn(List.of(existing));
        var indexer = new FileIndexerImpl(INDEX, directory, repository, observer, null, 1);
        ReflectionTestUtils.setField(indexer, "running", true);
        indexer.setInclusionPatterns(List.of(path -> {
            indexer.requestStop();
            return true;
        }));
        try {
            Object scan = ReflectionTestUtils.invokeMethod(indexer, "scanDirectoryAgainstDb");
            assertThat((Integer) ReflectionTestUtils.invokeMethod(scan, "changed")).isZero();
            assertThat((Integer) ReflectionTestUtils.invokeMethod(scan, "missing")).isZero();
            verify(repository, never()).save(any());
            verifyNoInteractions(observer);
        } finally {
            indexer.shutdown();
        }
    }

    @Test
    void committedCallbackAfterShutdownLeavesScheduledFileForRestart(@TempDir Path directory) {
        IndexerRepository repository = mock(IndexerRepository.class);
        FileIndexerObserver observer = mock(FileIndexerObserver.class);
        FileIndexerImpl indexer = new FileIndexerImpl(INDEX, directory, repository, observer, null, 1);
        IndexedFile file = new IndexedFile();
        file.setId(17L);
        file.setIndexerStatus(IndexedFile.IndexerStatus.SCHEDULED);
        CountDownLatch completion = new CountDownLatch(1);
        AtomicInteger pending = new AtomicInteger(1);
        indexer.shutdown();

        Runnable finished = () -> {
            completion.countDown();
            pending.decrementAndGet();
        };
        ReflectionTestUtils.invokeMethod(indexer, "notifyObserver", file, FileIndexerImpl.EventType.CREATE, finished);

        assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.SCHEDULED);
        assertThat(completion.getCount()).isZero();
        assertThat(pending).hasValue(0);
        verifyNoInteractions(observer, repository);
    }

    @Test
    void shutdownDuringBootstrapDoesNotReviveTheWatcherOrLeaveThreadsRunning(@TempDir Path directory)
            throws Exception {
        IndexerRepository repository = mock(IndexerRepository.class);
        FileIndexerObserver observer = mock(FileIndexerObserver.class);
        CountDownLatch readingSnapshot = new CountDownLatch(1);
        CountDownLatch interruptedSnapshot = new CountDownLatch(1);
        when(repository.findScanEntriesAfterId(eq(INDEX), eq(0L), any())).thenAnswer(invocation -> {
            readingSnapshot.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interruptedSnapshot.countDown();
                Thread.currentThread().interrupt();
            }
            return List.of();
        });
        FileIndexerImpl indexer = new FileIndexerImpl(INDEX, directory, repository, observer, null, 1);
        try {
            indexer.start(false);
            assertThat(readingSnapshot.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            indexer.shutdown();

            assertThat(interruptedSnapshot.getCount()).isZero();
            assertThat(indexer.isScanInProgress()).isFalse();
            assertThat(((Thread) ReflectionTestUtils.getField(indexer, "bootThread")).isAlive()).isFalse();
            assertThat(ReflectionTestUtils.getField(indexer, "watchService")).isNull();
            assertThat(((ExecutorService) ReflectionTestUtils.getField(indexer, "workerPool")).isTerminated()).isTrue();
            verifyNoInteractions(observer);

            indexer.start(false);
            assertThat(indexer.isScanInProgress()).isFalse();
            verify(repository).findScanEntriesAfterId(eq(INDEX), eq(0L), any());
        } finally {
            indexer.shutdown();
        }
    }
}
