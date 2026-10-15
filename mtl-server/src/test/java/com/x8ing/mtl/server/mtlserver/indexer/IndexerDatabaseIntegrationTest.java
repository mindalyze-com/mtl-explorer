package com.x8ing.mtl.server.mtlserver.indexer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexedFileScanEntry;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.event.ProcessingFileIndexerObserver;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexer;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaProcessingWorker;
import com.x8ing.mtl.server.mtlserver.testsupport.NoScheduledJobs;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapServerKickoffService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;

/** Synthetic sources, real PostgreSQL transactions, actual media metadata extraction. */
@EnabledIfSystemProperty(named = "mtl.test.indexer.jdbc-url", matches = "jdbc:postgresql:.*")
@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false", "mtl.garmin-sync.enabled=false"})
@Import(NoScheduledJobs.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IndexerDatabaseIntegrationTest {
    private static final FileTime ORIGINAL_TIME = FileTime.from(Instant.parse("2026-08-01T10:00:00Z"));
    private static final FileTime CHANGED_TIME = FileTime.from(Instant.parse("2026-08-01T11:00:00Z"));
    private static final FileTime LATEST_TIME = FileTime.from(Instant.parse("2026-08-01T12:00:00Z"));
    private static final int AWAIT_SECONDS = 20;
    private static final int INDEX_SUFFIX_LENGTH = 16;

    @TempDir
    Path directory;
    @MockitoSpyBean
    IndexerRepository files;
    @Autowired
    MediaRepository media;
    @Autowired
    IndexerStatusService statusService;
    @Autowired
    MediaProcessingWorker worker;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoSpyBean
    MediaIndexer metadata;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService mediaService;
    @MockitoBean
    MapServerKickoffService mapKickoff;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("mtl.test.indexer.jdbc-url"));
    }

    @Test
    @EnabledIfSystemProperty(named = "mtl.test.indexer.large-scan", matches = "true")
    void largeDatabaseScanFitsTheOriginalHeapWithoutRetainingFilesystemOrWorkLists() throws Exception {
        int sources = 126_254;
        long heapLimit = 320L * 1024 * 1024;
        assertThat(Runtime.getRuntime().maxMemory()).isLessThanOrEqualTo(heapLimit);
        String index = newIndex();
        Path template = write("attributes.jpg", 16, ORIGINAL_TIME);
        var attributes = Files.readAttributes(template, java.nio.file.attribute.BasicFileAttributes.class);
        var rootAttributes = Files.readAttributes(directory, java.nio.file.attribute.BasicFileAttributes.class);
        // Seed already indexed synthetic sources; freshness events are irrelevant to fixture loading.
        jdbc.execute("ALTER TABLE indexed_file DISABLE TRIGGER indexed_file_data_freshness");
        try {
            jdbc.update("""
                    INSERT INTO indexed_file ("index", name, path, size, last_modified_date,
                        indexer_status, indexer_invocations, work_generation, pending_operation, version)
                    SELECT ?, 'memory-' || n || '.jpg', '', ?, ?, 'COMPLETED_WITH_SUCCESS', 1, 1, 'UPSERT', 0
                    FROM generate_series(1, ?) n
                    """, index, attributes.size(), java.sql.Timestamp.from(ORIGINAL_TIME.toInstant()), sources);
        } finally {
            jdbc.execute("ALTER TABLE indexed_file ENABLE TRIGGER indexed_file_data_freshness");
        }
        jdbc.execute("ANALYZE indexed_file");
        // A lazy filesystem stream isolates database/scan heap use from mounted-drive latency.
        // stubOnly avoids retaining 126,254 mock invocations as part of the measured heap.
        try (var filesystem = org.mockito.Mockito.mockStatic(Files.class,
                org.mockito.Mockito.withSettings().defaultAnswer(org.mockito.Mockito.CALLS_REAL_METHODS).stubOnly());
             RunningIndexer run = new RunningIndexer(index, directory)) {
            filesystem.when(() -> Files.walk(directory)).thenAnswer(call -> java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(directory), java.util.stream.IntStream.rangeClosed(1, sources)
                            .mapToObj(n -> directory.resolve("memory-" + n + ".jpg"))));
            filesystem.when(() -> Files.readAttributes(org.mockito.ArgumentMatchers.any(Path.class),
                    org.mockito.ArgumentMatchers.eq(java.nio.file.attribute.BasicFileAttributes.class)))
                    .thenAnswer(call -> {
                        Path path = call.getArgument(0);
                        if (directory.equals(path)) return rootAttributes;
                        if (directory.equals(path.getParent()) && path.getFileName().toString().startsWith("memory-")) return attributes;
                        return call.callRealMethod();
                    });
            org.mockito.Mockito.clearInvocations(files);

            run.indexer.start(true);

            assertThat(run.indexer.isScanInProgress()).isFalse();
            org.mockito.Mockito.verify(files, org.mockito.Mockito.never()).findByIndex(index);
            org.mockito.Mockito.verify(files, org.mockito.Mockito.never()).saveAll(org.mockito.ArgumentMatchers.any());
            assertThat(files.countByIndexAndIndexerStatus(index, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS)).isEqualTo(sources);
            assertThat(files.countByIndexAndIndexerStatus(index, IndexedFile.IndexerStatus.SCHEDULED)).isZero();
        }
    }

    @Test
    void discoveryCommitsIndependentlyAndRestartDrainsWorkWithoutMemoryNotifications() throws Exception {
        for (int i = 0; i < 3; i++) write("discovered-" + i + ".jpg", 16, ORIGINAL_TIME);
        String index = newIndex();
        try (RunningIndexer discovery = new RunningIndexer(index, directory)) {
            ReflectionTestUtils.setField(discovery.indexer, "running", true);
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                ReflectionTestUtils.invokeMethod(discovery.indexer, "scanDirectoryAgainstDb", false);
                transaction.setRollbackOnly();
            });
            assertThat(files.findByIndex(index)).hasSize(3).allSatisfy(file -> {
                assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.SCHEDULED);
                assertThat(file.getWorkGeneration()).isZero();
                assertThat(media.findAllByIndexedFileId(file.getId())).isEmpty();
            });
        }
        try (RunningIndexer restarted = new RunningIndexer(index, directory)) {
            restarted.indexer.start(true);
            assertThat(files.findByIndex(index)).hasSize(3).allSatisfy(file -> {
                assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
                assertThat(file.getWorkGeneration()).isEqualTo(1);
                assertThat(media.findAllByIndexedFileId(file.getId())).hasSize(1);
            });
        }
    }

    @Test
    void blockedImportStillExposesTheWholeCommittedBacklog() throws Exception {
        int sources = 100;
        for (int i = 0; i < sources; i++) write("backlog-" + i + ".jpg", 16, ORIGINAL_TIME);
        String index = newIndex();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var observer = new ProcessingFileIndexerObserver(LoggerFactory.getLogger(getClass()), "Media",
                (name, id, changed) -> {
                    started.countDown();
                    try {
                        assertThat(release.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Synthetic import interrupted", exception);
                    }
                    worker.processCreateOrChange(name, id, changed);
                }, worker::processDelete);
        FileIndexerImpl indexer = new FileIndexerImpl(index, directory, files, observer, transactions, 1);
        indexer.setLiveWatchEnabled(false);
        statusService.registerScanState(index, indexer::isScanInProgress);
        statusService.registerDiscoveryState(index, indexer::isDiscoveryInProgress);
        try {
            indexer.start(false);
            assertThat(started.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(files.countByIndexAndIndexerStatus(index, IndexedFile.IndexerStatus.SCHEDULED)).isEqualTo(sources - 1);
            assertThat(files.countByIndexAndIndexerStatus(index, IndexedFile.IndexerStatus.PROCESSING)).isEqualTo(1);
            var summary = statusService.getIndexSummaries().stream().filter(item -> item.index().equals(index)).findFirst().orElseThrow();
            assertThat(summary.total()).isEqualTo(sources);
            assertThat(summary.pending()).isEqualTo(sources);
            assertThat(summary.completed()).isZero();
            assertThat(summary.discoveryInProgress()).isFalse();
            release.countDown();
            await(() -> !indexer.isScanInProgress());
            assertThat(files.countByIndexAndIndexerStatus(index, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS)).isEqualTo(sources);
        } finally {
            release.countDown();
            indexer.shutdown();
        }
    }

    @Test
    void unchangedRestorationAfterCommittedRemovalIntentKeepsTheExistingImportOnRestart() throws Exception {
        Path source = write("cancelled-removal.jpg", 16, ORIGINAL_TIME);
        byte[] original = Files.readAllBytes(source);
        String index = newIndex();
        long mediaId;
        try (RunningIndexer discovery = new RunningIndexer(index, directory)) {
            discovery.indexer.start(true);
            mediaId = media.findAllByIndexedFileId(file(index).getId()).getFirst().getId();
            Files.delete(source);
            // Commit discovery, then stop before any removal callback is dispatched.
            ReflectionTestUtils.invokeMethod(discovery.indexer, "scanDirectoryAgainstDb", false);
            assertThat(file(index).getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.SCHEDULED);
            assertThat(file(index).getPendingOperation()).isEqualTo(IndexedFile.Operation.DELETE);
        }
        Files.write(source, original);
        Files.setLastModifiedTime(source, ORIGINAL_TIME);
        try (RunningIndexer restarted = new RunningIndexer(index, directory)) {
            restarted.indexer.start(true);
            assertThat(file(index).getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            assertThat(file(index).getWorkGeneration()).isEqualTo(1);
            assertThat(file(index).getIndexerInvocations()).isEqualTo(1);
            assertThat(media.findAllByIndexedFileId(file(index).getId())).singleElement()
                    .satisfies(item -> assertThat(item.getId()).isEqualTo(mediaId));
        }
    }

    @Test
    void changedDiscoveryThenRemovalAndRestorationStillImportsTheChangedContent() throws Exception {
        Path source = write("changed-restoration.jpg", 16, ORIGINAL_TIME);
        String index = newIndex();
        long mediaId;
        byte[] replacement;
        try (RunningIndexer discovery = new RunningIndexer(index, directory)) {
            discovery.indexer.start(true);
            mediaId = media.findAllByIndexedFileId(file(index).getId()).getFirst().getId();
            write("changed-restoration.jpg", 32, CHANGED_TIME);
            replacement = Files.readAllBytes(source);
            ReflectionTestUtils.invokeMethod(discovery.indexer, "scanDirectoryAgainstDb", false);
            Files.delete(source);
            ReflectionTestUtils.invokeMethod(discovery.indexer, "scanDirectoryAgainstDb", false);
            assertThat(file(index).getLastSuccessfulGeneration()).isNull();
        }
        Files.write(source, replacement);
        Files.setLastModifiedTime(source, CHANGED_TIME);
        try (RunningIndexer restarted = new RunningIndexer(index, directory)) {
            restarted.indexer.start(true);
            assertThat(file(index).getWorkGeneration()).isEqualTo(2);
            assertThat(media.findAllByIndexedFileId(file(index).getId())).singleElement().satisfies(item -> {
                assertThat(item.getId()).isEqualTo(mediaId);
                assertThat(item.getWidthPixels()).isEqualTo(32);
            });
        }
    }

    @Test
    void failedDiscoveryCommitDoesNotDispatchAnImport() throws Exception {
        write("uncommitted.jpg", 16, ORIGINAL_TIME);
        String index = newIndex();
        AtomicBoolean unavailable = new AtomicBoolean(true);
        var delegate = mockingDetails(files).getMockCreationSettings().getDefaultAnswer();
        doAnswer(call -> {
            IndexedFile file = call.getArgument(0);
            if (unavailable.get() && file.getIndex().equals(index)) throw new TransientDataAccessResourceException("Synthetic discovery outage");
            return delegate.answer(call);
        }).when(files).saveAndFlush(any(IndexedFile.class));
        try (RunningIndexer run = new RunningIndexer(index, directory)) {
            run.indexer.start(true);
            assertThat(files.findByIndex(index)).isEmpty();
            assertThat(run.indexer.isScanInProgress()).isFalse();
            assertThat(ReflectionTestUtils.getField(run.indexer, "running")).isEqualTo(true);
            unavailable.set(false);
            assertThat(run.indexer.rescan()).isEqualTo(FileIndexerImpl.RescanRequestStatus.STARTED);
            await(() -> !run.indexer.isScanInProgress());
            assertThat(file(index).getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            assertThat(media.findAllByIndexedFileId(file(index).getId())).hasSize(1);
        }
    }

    @Test
    void failedDiscoveryCommitRollsBackAndRecoversOnRescan() throws Exception {
        write("available.jpg", 16, ORIGINAL_TIME);
        write("unavailable.jpg", 32, ORIGINAL_TIME);
        String index = newIndex();
        AtomicBoolean unavailable = new AtomicBoolean(true);
        AtomicInteger injectedFailures = new AtomicInteger();
        var delegate = mockingDetails(files).getMockCreationSettings().getDefaultAnswer();
        doAnswer(call -> {
            IndexedFile file = call.getArgument(0);
            Object result = delegate.answer(call);
            if (unavailable.get() && file.getIndex().equals(index) && file.getName().equals("unavailable.jpg")) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM indexed_file WHERE id = ?", Long.class, file.getId()))
                        .isEqualTo(1);
                injectedFailures.incrementAndGet();
                throw new TransientDataAccessResourceException("Synthetic discovery failure after flush");
            }
            return result;
        }).when(files).saveAndFlush(any(IndexedFile.class));
        try (RunningIndexer run = new RunningIndexer(index, directory)) {
            run.indexer.start(true);
            int failuresBeforeRescan = injectedFailures.get();
            assertThat(failuresBeforeRescan).isPositive();
            assertThat(files.findByIndexAndNameAndPath(index, "unavailable.jpg", "")).isEmpty();
            assertThat(files.findByIndex(index)).singleElement().satisfies(file -> {
                assertThat(file.getName()).isEqualTo("available.jpg");
                assertThat(file.getWorkGeneration()).isEqualTo(1);
                assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
                assertThat(media.findAllByIndexedFileId(file.getId())).hasSize(1);
            });
            unavailable.set(false);
            assertThat(run.indexer.rescan()).isEqualTo(FileIndexerImpl.RescanRequestStatus.STARTED);
            await(() -> !run.indexer.isScanInProgress());
            assertThat(files.findByIndex(index)).hasSize(2).allSatisfy(file -> {
                assertThat(file.getWorkGeneration()).isEqualTo(1);
                assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
                assertThat(media.findAllByIndexedFileId(file.getId())).hasSize(1);
            });
            assertThat(injectedFailures).hasValue(failuresBeforeRescan);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"40P01", "40001"})
    void discoveryRetriesRolledBackTransactionWithoutDuplicateImports(String sqlState) throws Exception {
        write("transaction-retry.jpg", 16, ORIGINAL_TIME);
        String index = newIndex();
        AtomicBoolean conflict = new AtomicBoolean(true);
        AtomicInteger injectedFailures = new AtomicInteger();
        var delegate = mockingDetails(files).getMockCreationSettings().getDefaultAnswer();
        doAnswer(call -> {
            IndexedFile file = call.getArgument(0);
            Object result = delegate.answer(call);
            if (file.getIndex().equals(index) && conflict.compareAndSet(true, false)) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM indexed_file WHERE id = ?", Long.class, file.getId()))
                        .isEqualTo(1);
                injectedFailures.incrementAndGet();
                throw new CannotAcquireLockException("Synthetic transaction rollback after flush",
                        new SQLException("retryable transaction failure", sqlState));
            }
            return result;
        }).when(files).saveAndFlush(any(IndexedFile.class));

        try (RunningIndexer run = new RunningIndexer(index, directory)) {
            run.indexer.start(true);

            assertThat(injectedFailures).hasValue(1);
            assertThat(files.findByIndex(index)).singleElement().satisfies(file -> {
                assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
                assertThat(file.getWorkGeneration()).isEqualTo(1);
                assertThat(file.getIndexerInvocations()).isEqualTo(1);
                assertThat(media.findAllByIndexedFileId(file.getId())).singleElement()
                        .satisfies(item -> assertThat(item.getWidthPixels()).isEqualTo(16));
            });
        }
    }

    @Test
    void staleScanReusesLiveDiscoveryWithoutAttemptingADuplicateInsert() throws Exception {
        Path source = write("live-discovery.jpg", 16, ORIGINAL_TIME);
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
        String index = newIndex();
        AtomicBoolean liveDiscovery = new AtomicBoolean(true);
        AtomicLong discoveredId = new AtomicLong();
        var delegate = mockingDetails(files).getMockCreationSettings().getDefaultAnswer();
        Logger logger = (Logger) LoggerFactory.getLogger(FileIndexerImpl.class);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            try (RunningIndexer scan = new RunningIndexer(index, directory)) {
                doAnswer(call -> {
                    List<IndexedFileScanEntry> snapshot = (List<IndexedFileScanEntry>) delegate.answer(call);
                    if (liveDiscovery.compareAndSet(true, false)) {
                        assertThat(snapshot).isEmpty();
                        // The watcher commits after the scan's lookup, before its discovery write.
                        IndexedFile discovered = CompletableFuture.supplyAsync(() ->
                                scan.indexer.registerDiscoveredWork(source, attributes)).get(AWAIT_SECONDS, TimeUnit.SECONDS);
                        discoveredId.set(discovered.getId());
                    }
                    return snapshot;
                }).when(files).findScanEntries(eq(index), anyString(), any());
                ReflectionTestUtils.setField(scan.indexer, "running", true);

                scan.indexer.scanDirectoryAgainstDb(false);

                assertThat(liveDiscovery).isFalse();
                assertThat(files.findByIndex(index)).singleElement().satisfies(file -> {
                    assertThat(file.getId()).isEqualTo(discoveredId.get());
                    assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.SCHEDULED);
                    assertThat(file.getWorkGeneration()).isZero();
                });
                assertThat(logs.list).filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).isEmpty();
            }
            try (RunningIndexer restarted = new RunningIndexer(index, directory)) {
                restarted.indexer.start(true);
                assertThat(files.findByIndex(index)).singleElement().satisfies(file -> {
                    assertThat(file.getId()).isEqualTo(discoveredId.get());
                    assertThat(file.getWorkGeneration()).isEqualTo(1);
                    assertThat(file.getIndexerInvocations()).isEqualTo(1);
                    assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
                    assertThat(media.findAllByIndexedFileId(file.getId())).singleElement()
                            .satisfies(item -> assertThat(item.getWidthPixels()).isEqualTo(16));
                });
            }
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    void staleDiscoveryChunkReloadsConcurrentStateBeforeImporting() throws Exception {
        write("concurrent-state.jpg", 16, ORIGINAL_TIME);
        String index = newIndex();
        try (RunningIndexer run = new RunningIndexer(index, directory)) {
            run.indexer.start(true);
            long id = file(index).getId();
            long mediaId = media.findAllByIndexedFileId(id).getFirst().getId();
            var delegate = mockingDetails(files).getMockCreationSettings().getDefaultAnswer();
            AtomicBoolean concurrentWrite = new AtomicBoolean(true);
            doAnswer(call -> {
                List<IndexedFileScanEntry> snapshot = (List<IndexedFileScanEntry>) delegate.answer(call);
                if (concurrentWrite.compareAndSet(true, false)) {
                    new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                        IndexedFile current = files.findById(id).orElseThrow();
                        current.setLastMessage("Synthetic concurrent state update");
                        files.saveAndFlush(current);
                    });
                }
                return snapshot;
            }).when(files).findScanEntries(eq(index), anyString(), any());
            clearInvocations(files);
            write("concurrent-state.jpg", 32, CHANGED_TIME);

            assertThat(run.indexer.rescan()).isEqualTo(FileIndexerImpl.RescanRequestStatus.STARTED);
            await(() -> !run.indexer.isScanInProgress());

            assertThat(concurrentWrite).isFalse();
            verify(files, atLeastOnce()).findByIndexAndNameAndPath(index, "concurrent-state.jpg", "");
            assertThat(file(index).getWorkGeneration()).isEqualTo(2);
            assertThat(file(index).getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            assertThat(media.findAllByIndexedFileId(id)).singleElement().satisfies(item -> {
                assertThat(item.getId()).isEqualTo(mediaId);
                assertThat(item.getWidthPixels()).isEqualTo(32);
            });
        }
    }

    @Test
    void liveWatcherBurstImportsTheLatestRevisionOffControlThreads() throws Exception {
        String index = newIndex();
        ConcurrentLinkedQueue<String> databaseThreads = new ConcurrentLinkedQueue<>();
        var delegate = mockingDetails(files).getMockCreationSettings().getDefaultAnswer();
        doAnswer(call -> {
            databaseThreads.add(Thread.currentThread().getName());
            return delegate.answer(call);
        }).when(files).findByIndexAndNameAndPath(eq(index), anyString(), anyString());
        try (RunningIndexer run = new RunningIndexer(index, directory)) {
            run.indexer.setLiveWatchEnabled(true);
            run.indexer.start(false);
            await(() -> !run.indexer.isScanInProgress() && ReflectionTestUtils.getField(run.indexer, "watcherThread") != null);
            for (int i = 0; i < 20; i++) write("live-burst.jpg", 16 + i, LATEST_TIME);
            await(() -> !run.indexer.isScanInProgress() && files.findByIndex(index).stream()
                    .anyMatch(file -> file.getIndexerStatus() == IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS));
            assertThat(files.findByIndex(index)).singleElement().satisfies(file -> {
                assertThat(file.getWorkGeneration()).isEqualTo(1);
                assertThat(file.getIndexerInvocations()).isEqualTo(1);
                assertThat(media.findAllByIndexedFileId(file.getId())).singleElement()
                        .satisfies(item -> assertThat(item.getWidthPixels()).isEqualTo(35));
            });
            assertThat(databaseThreads).anyMatch(name -> name.startsWith("idx-discover"))
                    .noneMatch(name -> name.startsWith("watch-") || name.startsWith("idx-deb"));
        }
    }

    @Test
    void completionWriteFailureRetainsCommittedMediaAndRetriesOnlyTheOutcome() throws Exception {
        write("completion.jpg", 16, ORIGINAL_TIME);
        String index = newIndex();
        AtomicBoolean unavailable = new AtomicBoolean(true);
        AtomicBoolean rejected = new AtomicBoolean();
        var delegate = org.mockito.Mockito.mockingDetails(files).getMockCreationSettings().getDefaultAnswer();
        doAnswer(call -> {
            IndexedFile file = call.getArgument(0);
            if (file.getIndexerStatus() == IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS && unavailable.get()) {
                rejected.set(true);
                throw new org.springframework.dao.TransientDataAccessResourceException("synthetic completion outage");
            }
            return delegate.answer(call);
        }).when(files).save(any(IndexedFile.class));
        try (RunningIndexer run = new RunningIndexer(index, directory)) {
            run.indexer.start(false);
            await(rejected::get);
            long id = file(index).getId();
            long mediaId = media.findAllByIndexedFileId(id).getFirst().getId();
            assertThat(run.indexer.isScanInProgress()).isTrue();
            assertThat(file(index).getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.PROCESSING);
            unavailable.set(false);
            await(() -> !run.indexer.isScanInProgress());
            assertThat(file(index).getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            assertThat(file(index).getWorkGeneration()).isEqualTo(1);
            assertThat(file(index).getIndexerInvocations()).isEqualTo(1);
            assertThat(media.findAllByIndexedFileId(id)).singleElement().satisfies(item -> assertThat(item.getId()).isEqualTo(mediaId));
        }
    }

    @Test
    void committedSchedulingUsesAnIndependentConsumerTransactionAndRestartReplaysSafely() throws Exception {
        Path source = write("restart.jpg", 16, ORIGINAL_TIME);
        String index = newIndex();
        long mediaId;
        try (RunningIndexer run = new RunningIndexer(index, directory)) {
            run.indexer.start(true);
            IndexedFile file = file(index);
            assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            assertThat(file.getWorkGeneration()).isEqualTo(1);
            mediaId = media.findAllByIndexedFileId(file.getId()).getFirst().getId();
            // Domain rows committed, but completion was lost before shutdown.
            file.setIndexerStatus(IndexedFile.IndexerStatus.PROCESSING);
            files.saveAndFlush(file);
        }
        try (RunningIndexer restarted = new RunningIndexer(index, directory)) {
            restarted.indexer.start(true);
            IndexedFile file = file(index);
            assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            assertThat(file.getWorkGeneration()).isEqualTo(2);
            assertThat(media.findAllByIndexedFileId(file.getId())).singleElement()
                    .satisfies(item -> assertThat(item.getId()).isEqualTo(mediaId));
            assertThat(Files.exists(source)).isTrue();
        }
    }

    @Test
    void changedSourceRollsBackMetadataAndAutomaticallyImportsItsLatestRevision() throws Exception {
        Path source = write("changing.jpg", 16, ORIGINAL_TIME);
        String index = newIndex();
        try (RunningIndexer run = new RunningIndexer(index, directory)) {
            run.indexer.start(true);
            long id = file(index).getId();
            long mediaId = media.findAllByIndexedFileId(id).getFirst().getId();
            AtomicBoolean changeDuringRead = new AtomicBoolean(true);
            MediaIndexer target = AopTestUtils.getUltimateTargetObject(metadata);
            doAnswer(call -> {
                call.callRealMethod();
                if (changeDuringRead.compareAndSet(true, false)) write("changing.jpg", 48, LATEST_TIME);
                return null;
            }).when(target).refreshFile(any(IndexedFile.class));
            write("changing.jpg", 32, CHANGED_TIME);

            run.indexer.rescan();

            await(() -> !changeDuringRead.get() && file(index).getWorkGeneration() == 2 && (file(index).getIndexerStatus() == IndexedFile.IndexerStatus.SCHEDULED
                    || file(index).getIndexerStatus() == IndexedFile.IndexerStatus.RESCHEDULED));
            assertThat(media.findAllByIndexedFileId(id).getFirst().getWidthPixels()).isEqualTo(16);
            await(() -> file(index).getWorkGeneration() == 3 && file(index).getIndexerStatus() == IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            assertThat(media.findAllByIndexedFileId(id)).singleElement().satisfies(item -> {
                assertThat(item.getId()).isEqualTo(mediaId);
                assertThat(item.getWidthPixels()).isEqualTo(48);
            });
            assertThat(file(index).getSize()).isEqualTo(Files.size(source));
        }
    }

    @Test
    void failedDeletionRollsBackDomainRowsAndManualRetryFinishesRemoval() throws Exception {
        Path source = write("deletion.jpg", 16, ORIGINAL_TIME);
        String index = newIndex();
        try (RunningIndexer run = new RunningIndexer(index, directory)) {
            run.indexer.start(true);
            long id = file(index).getId();
            AtomicBoolean fail = new AtomicBoolean(true);
            MediaIndexer target = AopTestUtils.getUltimateTargetObject(metadata);
            doAnswer(call -> {
                call.callRealMethod();
                if (fail.get()) throw new IllegalStateException("synthetic deletion transaction failure");
                return null;
            }).when(target).deleteFilesForIndexedFile(any(IndexedFile.class));
            Files.delete(source);

            run.indexer.rescan();
            await(() -> !run.indexer.isScanInProgress());

            assertThat(file(index).getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.FAILED);
            assertThat(file(index).getPendingOperation()).isEqualTo(IndexedFile.Operation.DELETE);
            assertThat(file(index).getLastMessage()).contains("synthetic deletion transaction failure");
            assertThat(media.findAllByIndexedFileId(id)).hasSize(1);
            fail.set(false);
            run.indexer.rescan();
            await(() -> !run.indexer.isScanInProgress());
            assertThat(file(index).getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.REMOVED);
            assertThat(media.findAllByIndexedFileId(id)).isEmpty();
        }
    }

    @Test
    void restoringSourceDuringDeletionRollsBackCleanupAndRetainsMediaIdentity() throws Exception {
        Path source = write("restored.jpg", 16, ORIGINAL_TIME);
        String index = newIndex();
        try (RunningIndexer run = new RunningIndexer(index, directory)) {
            run.indexer.start(true);
            long id = file(index).getId();
            long mediaId = media.findAllByIndexedFileId(id).getFirst().getId();
            AtomicBoolean restore = new AtomicBoolean(true);
            MediaIndexer target = AopTestUtils.getUltimateTargetObject(metadata);
            doAnswer(call -> {
                call.callRealMethod();
                if (restore.compareAndSet(true, false)) write("restored.jpg", 16, ORIGINAL_TIME);
                return null;
            }).when(target).deleteFilesForIndexedFile(any(IndexedFile.class));
            Files.delete(source);
            run.indexer.rescan();
            await(() -> file(index).getWorkGeneration() == 2 && (file(index).getIndexerStatus() == IndexedFile.IndexerStatus.SCHEDULED
                    || file(index).getIndexerStatus() == IndexedFile.IndexerStatus.RESCHEDULED));
            assertThat(media.findAllByIndexedFileId(id)).singleElement().satisfies(item -> assertThat(item.getId()).isEqualTo(mediaId));
            await(() -> file(index).getWorkGeneration() == 3 && file(index).getIndexerStatus() == IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            assertThat(media.findAllByIndexedFileId(id)).singleElement().satisfies(item -> assertThat(item.getId()).isEqualTo(mediaId));
            assertThat(Files.exists(source)).isTrue();
        }
    }

    @Test
    void inaccessibleRootPreservesCommittedDomainRows(@TempDir Path outside) throws Exception {
        write("retained.jpg", 16, ORIGINAL_TIME);
        String index = newIndex();
        try (RunningIndexer run = new RunningIndexer(index, directory)) {
            run.indexer.start(true);
            long id = file(index).getId();
            Path relocated = outside.resolve("unavailable-root");
            Files.move(directory, relocated);
            try {
                run.indexer.rescan();
                await(() -> !run.indexer.isScanInProgress());
                assertThat(file(index).getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
                assertThat(media.findAllByIndexedFileId(id)).hasSize(1);
                assertThat(Files.exists(relocated.resolve("retained.jpg"))).isTrue();
            } finally {
                Files.move(relocated, directory);
            }
        }
    }

    private Path write(String name, int width, FileTime time) throws Exception {
        Path path = directory.resolve(name);
        assertThat(ImageIO.write(new BufferedImage(width, 16, BufferedImage.TYPE_INT_RGB), "jpg", path.toFile())).isTrue();
        Files.setLastModifiedTime(path, time);
        return path;
    }

    private IndexedFile file(String index) { return files.findByIndex(index).getFirst(); }
    private String newIndex() { return "TEST_MEDIA_" + UUID.randomUUID().toString().substring(0, INDEX_SUFFIX_LENGTH); }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private final class RunningIndexer implements AutoCloseable {
        final FileIndexerImpl indexer;
        RunningIndexer(String index, Path root) {
            indexer = new FileIndexerImpl(index, root, files, new ProcessingFileIndexerObserver(
                    LoggerFactory.getLogger(IndexerDatabaseIntegrationTest.class), "Media", worker::processCreateOrChange, worker::processDelete), transactions, 2);
            indexer.setLiveWatchEnabled(false);
        }
        @Override
        public void close() { indexer.shutdown(); }
    }
}
