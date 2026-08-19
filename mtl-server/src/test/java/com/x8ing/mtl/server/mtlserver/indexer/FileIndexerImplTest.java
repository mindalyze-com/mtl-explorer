package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.event.FileIndexerObserver;
import com.x8ing.mtl.server.mtlserver.indexer.event.OnCompletion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileIndexerImplTest {

    private static final String INDEX = "GPS";
    private static final long FILE_ID = 17L;

    @Test
    void oldModificationTimeSatisfiesTheStabilityObservationWindow() {
        long nowMillis = Instant.parse("2026-08-19T16:00:10Z").toEpochMilli();
        Duration observationWindow = Duration.ofSeconds(2);

        assertThat(FileIndexerImpl.stableObservationWindowElapsed(
                FileTime.from(Instant.parse("2026-08-19T16:00:08Z")),
                nowMillis,
                observationWindow)).isTrue();
        assertThat(FileIndexerImpl.stableObservationWindowElapsed(
                FileTime.from(Instant.parse("2026-08-19T16:00:09Z")),
                nowMillis,
                observationWindow)).isFalse();
        assertThat(FileIndexerImpl.stableObservationWindowElapsed(
                FileTime.from(Instant.parse("2026-08-19T16:00:11Z")),
                nowMillis,
                observationWindow)).isFalse();
    }

    @Test
    void existingPathCreatedByAtomicReplacementIsProcessedAsChange(@TempDir Path watchDirectory) {
        IndexedFile indexedFile = indexedFile(
                watchDirectory.resolve("atomic-replacement.gpx"),
                100,
                FileTime.from(Instant.parse("2026-08-17T08:00:00Z")));

        assertThat(FileIndexerImpl.effectiveObserverEventType(FileIndexerImpl.EventType.CREATE, indexedFile))
                .isEqualTo(FileIndexerImpl.EventType.MODIFY);
    }

    @Test
    void recreatedRemovedPathIsProcessedAsChange(@TempDir Path watchDirectory) {
        IndexedFile indexedFile = indexedFile(
                watchDirectory.resolve("restored.gpx"),
                100,
                FileTime.from(Instant.parse("2026-08-17T08:00:00Z")));
        indexedFile.setIndexerStatus(IndexedFile.IndexerStatus.REMOVED);

        assertThat(FileIndexerImpl.effectiveObserverEventType(FileIndexerImpl.EventType.CREATE, indexedFile))
                .isEqualTo(FileIndexerImpl.EventType.MODIFY);
    }

    @Test
    void defaultStrategyReprocessesSameSizeGpxWhenModificationTimeChanges(@TempDir Path watchDirectory)
            throws Exception {
        Path gpxPath = watchDirectory.resolve("same-size-change.gpx");
        String originalGpx = "<gpx><name>A</name></gpx>";
        String modifiedGpx = "<gpx><name>B</name></gpx>";
        assertThat(modifiedGpx).hasSameSizeAs(originalGpx);

        FileTime originalModifiedTime = FileTime.from(Instant.parse("2026-08-17T08:00:00Z"));
        FileTime changedModifiedTime = FileTime.from(Instant.parse("2026-08-17T08:01:00Z"));
        Files.writeString(gpxPath, originalGpx, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(gpxPath, originalModifiedTime);

        IndexedFile indexedFile = indexedFile(gpxPath, Files.size(gpxPath), originalModifiedTime);

        IndexerRepository repository = mock(IndexerRepository.class);
        when(repository.findByIndex(INDEX)).thenReturn(List.of(indexedFile));
        when(repository.findByIndexAndIndexerStatus(eq(INDEX), any())).thenReturn(List.of());
        when(repository.findByIndexAndNameAndPath(INDEX, gpxPath.getFileName().toString(), ""))
                .thenReturn(Optional.of(indexedFile));
        when(repository.findById(FILE_ID)).thenReturn(Optional.of(indexedFile));
        when(repository.save(any(IndexedFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveAndFlush(any(IndexedFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileIndexerObserver observer = mock(FileIndexerObserver.class);
        doAnswer(invocation -> {
            OnCompletion completion = invocation.getArgument(2);
            completion.success(FILE_ID);
            return null;
        }).when(observer).onChangedFile(eq(INDEX), eq(FILE_ID), any(OnCompletion.class));
        FileIndexerImpl indexer = new FileIndexerImpl(
                INDEX,
                watchDirectory,
                repository,
                observer,
                null,
                1);
        indexer.setLiveWatchEnabled(false);

        try {
            indexer.start(true);
            verifyNoInteractions(observer);

            Files.writeString(gpxPath, modifiedGpx, StandardCharsets.UTF_8);
            Files.setLastModifiedTime(gpxPath, changedModifiedTime);
            assertThat(Files.size(gpxPath)).isEqualTo(indexedFile.getSize());

            assertThat(indexer.rescan()).isEqualTo(FileIndexerImpl.RescanRequestStatus.STARTED);

            verify(observer, timeout(2_000)).onChangedFile(eq(INDEX), eq(FILE_ID), any(OnCompletion.class));
            assertThat(indexedFile.getLastModifiedDate()).isEqualTo(Date.from(changedModifiedTime.toInstant()));
            assertThat(indexedFile.getIndexerStatus())
                    .isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        } finally {
            indexer.shutdown();
        }
    }

    @Test
    void initialScanRemainsInProgressUntilDomainProcessingCompletes(@TempDir Path watchDirectory)
            throws Exception {
        Path gpxPath = watchDirectory.resolve("slow-import.gpx");
        Files.writeString(gpxPath, "<gpx/>", StandardCharsets.UTF_8);

        IndexerRepository repository = mock(IndexerRepository.class);
        AtomicReference<IndexedFile> savedFile = new AtomicReference<>();
        when(repository.findByIndex(INDEX)).thenReturn(List.of());
        when(repository.findByIndexAndIndexerStatus(eq(INDEX), any())).thenReturn(List.of());
        when(repository.findByIndexAndNameAndPath(INDEX, gpxPath.getFileName().toString(), ""))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IndexedFile.class))).thenAnswer(invocation -> {
            IndexedFile indexedFile = invocation.getArgument(0);
            indexedFile.setId(FILE_ID);
            savedFile.set(indexedFile);
            return indexedFile;
        });
        when(repository.findById(FILE_ID)).thenAnswer(invocation -> Optional.ofNullable(savedFile.get()));
        when(repository.save(any(IndexedFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CountDownLatch observerStarted = new CountDownLatch(1);
        CountDownLatch releaseObserver = new CountDownLatch(1);
        FileIndexerObserver observer = new FileIndexerObserver() {
            @Override
            public void onNewFile(String index, long fileId) {
            }

            @Override
            public void onDeletedFile(String index, long fileId) {
            }

            @Override
            public void onChangedFile(String index, long fileId) {
            }

            @Override
            public void onNewFile(String index, long fileId, OnCompletion completion) {
                completion.started(fileId);
                observerStarted.countDown();
                try {
                    if (!releaseObserver.await(2, TimeUnit.SECONDS)) {
                        completion.failed(fileId, "test timeout");
                        return;
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    completion.failed(fileId, "test interrupted");
                    return;
                }
                completion.success(fileId);
            }
        };

        FileIndexerImpl indexer = new FileIndexerImpl(
                INDEX,
                watchDirectory,
                repository,
                observer,
                null,
                1);
        indexer.setLiveWatchEnabled(false);

        try {
            indexer.start(false);
            assertThat(observerStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(indexer.isScanInProgress()).isTrue();

            releaseObserver.countDown();

            awaitCondition(() -> !indexer.isScanInProgress(), 2_000);
            assertThat(savedFile.get().getIndexerStatus())
                    .isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        } finally {
            releaseObserver.countDown();
            indexer.shutdown();
        }
    }

    private static void awaitCondition(BooleanSupplier condition, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static IndexedFile indexedFile(Path path, long size, FileTime lastModifiedTime) {
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setId(FILE_ID);
        indexedFile.setIndex(INDEX);
        indexedFile.setName(path.getFileName().toString());
        indexedFile.setBasePath(path.getParent().toString());
        indexedFile.setFullPath(path.toString());
        indexedFile.setPath("");
        indexedFile.setSize(size);
        indexedFile.setLastModifiedDate(Date.from(lastModifiedTime.toInstant()));
        indexedFile.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        return indexedFile;
    }
}
