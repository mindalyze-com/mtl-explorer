package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.event.FileIndexerObserver;
import com.x8ing.mtl.server.mtlserver.indexer.event.OnCompletion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileIndexerRecoveryTest {
    private static final String INDEX = "MEDIA";
    private static final FileTime OLD_TIME = FileTime.from(Instant.parse("2026-08-01T10:00:00Z"));
    private static final int TIMEOUT_SECONDS = 5;

    @Test
    void failedDiscoveryContinuesOtherSourcesAndRecoversOnRescan(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 2)) {
            fixture.write("unavailable.jpg", "synthetic outage");
            fixture.write("available.jpg", "synthetic source");
            AtomicBoolean unavailable = new AtomicBoolean(true);
            doAnswer(call -> {
                IndexedFile file = call.getArgument(0);
                if (unavailable.get() && file.getName().equals("unavailable.jpg"))
                    throw new TransientDataAccessResourceException("Synthetic source outage");
                return fixture.save(file);
            }).when(fixture.repository).saveAndFlush(any(IndexedFile.class));

            fixture.indexer.start(true);

            assertThat(fixture.calls).hasValue(1);
            assertThat(fixture.repository.findByIndex(INDEX)).singleElement()
                    .satisfies(file -> assertThat(file.getName()).isEqualTo("available.jpg"));
            unavailable.set(false);
            fixture.rescan();
            assertThat(fixture.calls).hasValue(2);
            assertThat(fixture.repository.findByIndex(INDEX)).hasSize(2).allSatisfy(file ->
                    assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS));
        }
    }

    @Test
    void failedRemovalDiscoveryKeepsIndexerRunningAndCanBeRetried(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Path source = fixture.write("missing.jpg", "synthetic");
            fixture.seed(source, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            Files.delete(source);
            AtomicBoolean unavailable = new AtomicBoolean(true);
            doAnswer(call -> {
                IndexedFile file = call.getArgument(0);
                if (unavailable.get() && file.getPendingOperation() == IndexedFile.Operation.DELETE)
                    throw new TransientDataAccessResourceException("Synthetic removal discovery outage");
                return fixture.save(file);
            }).when(fixture.repository).saveAndFlush(any(IndexedFile.class));
            fixture.indexer.start(true);
            assertThat(fixture.calls).hasValue(0);
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            unavailable.set(false);
            fixture.rescan();
            assertThat(fixture.calls).hasValue(1);
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.REMOVED);
        }
    }

    @Test
    void discoveryCommitsEachSourceUsingCurrentPathIdentity(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            int sources = 1_001;
            for (int i = 0; i < sources; i++) fixture.write("batch-" + i + ".jpg", "synthetic");
            ReflectionTestUtils.setField(fixture.indexer, "running", true);

            ReflectionTestUtils.invokeMethod(fixture.indexer, "scanDirectoryAgainstDb", false);

            assertThat(fixture.repository.findByIndex(INDEX)).hasSize(sources).allSatisfy(file -> {
                assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.SCHEDULED);
                assertThat(file.getWorkGeneration()).isZero();
            });
            verify(fixture.repository, times(sources)).saveAndFlush(any(IndexedFile.class));
            verify(fixture.repository, never()).saveAll(any());
            verify(fixture.repository, times(sources)).findByIndexAndNameAndPath(eq(INDEX), anyString(), eq(""));
            assertThat(fixture.calls).hasValue(0);
        }
    }

    @Test
    void largeUnchangedScanNeverLoadsFullIndexEntities(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            int sources = 1_001;
            for (int i = 0; i < sources; i++) {
                fixture.seed(fixture.write("existing-" + i + ".jpg", "synthetic"), IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            }
            fixture.indexer.start(true);

            assertThat(fixture.calls).hasValue(0);
            verify(fixture.repository, never()).findByIndex(anyString());
            verify(fixture.repository, never()).findById(anyLong());
            verify(fixture.repository, never()).saveAll(any());
            var lookups = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
            verify(fixture.repository, atLeast(4)).findScanEntries(eq(INDEX), eq(""), lookups.capture());
            assertThat(lookups.getAllValues()).allSatisfy(names -> assertThat(names.size()).isBetween(1, 256));
        }
    }

    @Test
    void blockedImportKeepsTheFullDurableBacklogButBoundsMemoryWork(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            int sources = 513;
            for (int i = 0; i < sources; i++) fixture.write("bounded-" + i + ".jpg", "synthetic");
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            fixture.action = (type, id, completion) -> {
                started.countDown();
                assertThat(release.await(TIMEOUT_SECONDS * 2L, TimeUnit.SECONDS)).isTrue();
                completion.success(id);
            };
            fixture.indexer.start(false);
            try {
                assertThat(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThat(fixture.stored).hasSize(sources);
                assertThat(fixture.workers().getQueue().size()).isLessThan(128);
                assertThat(fixture.queuedWork().size()).isLessThan(128);
                verify(fixture.repository, never()).findByIndex(anyString());
            } finally {
                release.countDown();
            }
            await(() -> !fixture.indexer.isScanInProgress());
            assertThat(fixture.calls).hasValue(sources);
            assertThat(fixture.stored.values()).allSatisfy(file -> {
                assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
                assertThat(file.getWorkGeneration()).isEqualTo(1);
            });
        }
    }

    @Test
    void scanKeepsEqualNamesInDifferentDirectoriesDistinct(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Files.createDirectories(directory.resolve("one"));
            Files.createDirectories(directory.resolve("two"));
            Path first = fixture.write("one/shared.jpg", "first revision");
            Path second = fixture.write("two/shared.jpg", "second revision");
            fixture.seed(first, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            fixture.seed(second, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            fixture.indexer.start(true);
            assertThat(fixture.calls).hasValue(0);

            fixture.write("one/shared.jpg", "changed first revision");
            fixture.rescan();

            assertThat(fixture.calls).hasValue(1);
            assertThat(fixture.repository.findByIndexAndNameAndPath(INDEX, "shared.jpg", "one").orElseThrow().getSize()).isEqualTo(Files.size(first));
            assertThat(fixture.repository.findByIndexAndNameAndPath(INDEX, "shared.jpg", "two").orElseThrow().getWorkGeneration()).isZero();
        }
    }

    @Test
    void schedulingAndStartRetryOptimisticConflicts(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            fixture.write("retry.jpg", "synthetic");
            AtomicBoolean schedulingConflict = new AtomicBoolean(true);
            AtomicBoolean startConflict = new AtomicBoolean(true);
            doAnswer(call -> {
                IndexedFile file = call.getArgument(0);
                if (file.getWorkGeneration() == 1 && schedulingConflict.compareAndSet(true, false))
                    throw new ObjectOptimisticLockingFailureException(IndexedFile.class, file.getId());
                return fixture.save(file);
            }).when(fixture.repository).saveAndFlush(any(IndexedFile.class));
            doAnswer(call -> {
                IndexedFile file = call.getArgument(0);
                if (file.getIndexerStatus() == IndexedFile.IndexerStatus.PROCESSING && startConflict.compareAndSet(true, false))
                    throw new ObjectOptimisticLockingFailureException(IndexedFile.class, file.getId());
                return fixture.save(file);
            }).when(fixture.repository).save(any(IndexedFile.class));

            fixture.indexer.start(true);

            assertThat(schedulingConflict).isFalse();
            assertThat(startConflict).isFalse();
            assertThat(fixture.calls).hasValue(1);
            assertThat(fixture.file().getWorkGeneration()).isEqualTo(1);
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"40P01", "40001"})
    void discoveryRetriesTransactionRollbackBeforeImporting(String sqlState, @TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            fixture.write("transaction-retry.jpg", "synthetic discovery source");
            AtomicBoolean conflict = new AtomicBoolean(true);
            doAnswer(call -> {
                if (conflict.compareAndSet(true, false)) {
                    throw new CannotAcquireLockException("Synthetic transaction rollback",
                            new SQLException("retryable transaction failure", sqlState));
                }
                return fixture.save(call.getArgument(0));
            }).when(fixture.repository).saveAndFlush(any(IndexedFile.class));

            fixture.indexer.start(true);

            assertThat(conflict).isFalse();
            assertThat(fixture.calls).hasValue(1);
            assertThat(fixture.repository.findByIndex(INDEX)).singleElement().satisfies(file -> {
                assertThat(file.getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
                assertThat(file.getWorkGeneration()).isEqualTo(1);
                assertThat(file.getIndexerInvocations()).isEqualTo(1);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"40P01", "40001"})
    void persistentTransactionRollbackStopsAfterBoundedRetries(String sqlState, @TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            fixture.write("persistent-conflict.jpg", "synthetic discovery source");
            AtomicInteger attempts = new AtomicInteger();
            doAnswer(call -> {
                attempts.incrementAndGet();
                throw new CannotAcquireLockException("Synthetic persistent transaction rollback",
                        new SQLException("retryable transaction failure", sqlState));
            }).when(fixture.repository).saveAndFlush(any(IndexedFile.class));

            fixture.indexer.start(true);

            assertThat(attempts).hasValue(3);
            assertThat(fixture.calls).hasValue(0);
            assertThat(fixture.repository.findByIndex(INDEX)).isEmpty();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"08006", "23502"})
    void unrelatedDatabaseFailuresAreNotRetried(String sqlState, @TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            fixture.write("unavailable.jpg", "synthetic discovery source");
            AtomicInteger attempts = new AtomicInteger();
            doAnswer(call -> {
                attempts.incrementAndGet();
                throw new TransientDataAccessResourceException("Synthetic unrelated database failure",
                        new SQLException("not a retryable transaction conflict", sqlState));
            }).when(fixture.repository).saveAndFlush(any(IndexedFile.class));

            fixture.indexer.start(true);

            assertThat(attempts).hasValue(1);
            assertThat(fixture.calls).hasValue(0);
            assertThat(fixture.repository.findByIndex(INDEX)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void discoveryReloadsAfterConcurrentStateOrInsertConflict(boolean insertConflict, @TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Path source = fixture.write("concurrent.jpg", "synthetic");
            AtomicBoolean conflict = new AtomicBoolean(true);
            doAnswer(call -> {
                IndexedFile file = call.getArgument(0);
                if (conflict.compareAndSet(true, false)) {
                    fixture.save(file); // Simulate a competing transaction committing this path.
                    if (insertConflict) throw new DataIntegrityViolationException("Synthetic concurrent insert", new SQLException("unique path", "23505"));
                    throw new ObjectOptimisticLockingFailureException(IndexedFile.class, file.getId());
                }
                return fixture.save(file);
            }).when(fixture.repository).saveAndFlush(any(IndexedFile.class));

            ReflectionTestUtils.invokeMethod(fixture.indexer, "registerDiscoveredWork", source, Files.readAttributes(source, BasicFileAttributes.class));

            verify(fixture.repository, times(2)).findByIndexAndNameAndPath(INDEX, "concurrent.jpg", "");
            assertThat(fixture.repository.findByIndex(INDEX)).hasSize(1);
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.SCHEDULED);
            assertThat(fixture.calls).hasValue(0);
        }
    }

    @Test
    void blockedStateTransactionDoesNotBlockAnotherPath(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 2)) {
            Path blocked = fixture.write("blocked.jpg", "synthetic blocked");
            Path free = fixture.write("free.jpg", "synthetic free");
            Object blockedMonitor = ReflectionTestUtils.invokeMethod(fixture.indexer, "monitorFor", blocked);
            assertThat(blockedMonitor)
                    .isNotSameAs(ReflectionTestUtils.invokeMethod(fixture.indexer, "monitorFor", free));
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean block = new AtomicBoolean(true);
            doAnswer(call -> {
                IndexedFile file = call.getArgument(0);
                if (file.getName().equals("blocked.jpg") && file.getWorkGeneration() == 1 && block.compareAndSet(true, false)) {
                    entered.countDown();
                    assertThat(release.await(TIMEOUT_SECONDS * 2L, TimeUnit.SECONDS)).isTrue();
                }
                return fixture.save(file);
            }).when(fixture.repository).saveAndFlush(any(IndexedFile.class));
            fixture.indexer.start(false);
            try {
                assertThat(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                await(() -> fixture.repository.findByIndexAndNameAndPath(INDEX, "free.jpg", "")
                        .map(file -> file.getIndexerStatus() == IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS).orElse(false));
                assertThat(fixture.calls).hasValue(1);
            } finally {
                release.countDown();
            }
            await(() -> fixture.calls.get() == 2 && !fixture.indexer.isScanInProgress());
        }
    }

    @Test
    void watcherBurstCoalescesDatabaseWorkAndLeavesSchedulerFree(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            fixture.indexer.start(true);
            Path source = fixture.write("burst.jpg", "synthetic");
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch timerAvailable = new CountDownLatch(1);
            AtomicInteger discoveries = new AtomicInteger();
            doAnswer(call -> {
                discoveries.incrementAndGet();
                assertThat(Thread.currentThread().getName()).startsWith("idx-discover");
                entered.countDown();
                assertThat(release.await(TIMEOUT_SECONDS * 2L, TimeUnit.SECONDS)).isTrue();
                return Optional.empty();
            }).when(fixture.repository).findByIndexAndNameAndPath(INDEX, "burst.jpg", "");
            try {
                for (int i = 0; i < 100; i++)
                    ReflectionTestUtils.invokeMethod(fixture.indexer, "onFsEvent", source, FileIndexerImpl.EventType.MODIFY);
                assertThat(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThat(discoveries).hasValue(1);
                assertThat(fixture.indexer.isScanInProgress()).isTrue();
                ScheduledExecutorService timers = (ScheduledExecutorService) ReflectionTestUtils.getField(fixture.indexer, "scheduler");
                timers.execute(timerAvailable::countDown);
                assertThat(timerAvailable.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
            await(() -> !fixture.repository.findByIndex(INDEX).isEmpty());
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.SCHEDULED);
        }
    }

    @Test
    void directoryWithASourceExtensionDoesNotCreatePendingWork(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Path folder = Files.createDirectory(directory.resolve("not-a-source.jpg"));
            fixture.indexer.start(true);
            fixture.dispatch(folder, FileIndexerImpl.EventType.CREATE);
            await(() -> fixture.workers().getQueue().isEmpty() && !fixture.indexer.isScanInProgress());
            assertThat(fixture.repository.findByIndex(INDEX)).isEmpty();
            assertThat(fixture.calls.get()).isZero();
        }
    }

    @Test
    void sourceReplacedByADirectoryIsVisibleAsFailedWithoutCleanup(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Path source = fixture.write("replaced.jpg", "synthetic");
            fixture.seed(source, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            fixture.indexer.start(true);
            Files.delete(source);
            Files.createDirectory(source);
            fixture.rescan();
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.FAILED);
            assertThat(fixture.file().getLastMessage()).isEqualTo("Source path is not a regular file");
            assertThat(fixture.calls.get()).isZero();
        }
    }

    @Test
    void directoryReplacementDuringActiveImportPreservesOwnershipUntilCompletion(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Path source = fixture.write("active-directory.jpg", "original");
            fixture.seed(source, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            fixture.indexer.start(true);
            AtomicReference<OnCompletion> active = new AtomicReference<>();
            fixture.action = (type, id, completion) -> active.set(completion);
            fixture.write("active-directory.jpg", "changed source");
            fixture.dispatch(source, FileIndexerImpl.EventType.MODIFY);
            await(() -> active.get() != null);
            Files.delete(source);
            Files.createDirectory(source);

            assertThat(fixture.indexer.rescan()).isEqualTo(FileIndexerImpl.RescanRequestStatus.STARTED);
            await(() -> fixture.file().getIndexerStatus() == IndexedFile.IndexerStatus.RESCHEDULED);
            assertThat(fixture.indexer.isScanInProgress()).isTrue();
            assertThat(fixture.calls).hasValue(1);
            active.get().success(fixture.file().getId());

            await(() -> !fixture.indexer.isScanInProgress());
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.FAILED);
            assertThat(fixture.file().getLastMessage()).isEqualTo("Source path is not a regular file");
            assertThat(fixture.calls).hasValue(1);
        }
    }

    @Test
    void failedRootScanPreservesIndexedRows(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Path file = fixture.write("retained.jpg", "synthetic");
            fixture.seed(file, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            fixture.indexer.start(true);
            Files.delete(file);
            Files.delete(directory);

            fixture.rescan();

            assertThat(fixture.calls.get()).isZero();
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        }
    }

    @Test
    void incompleteWalkRetainsKnownChangesWithoutDispatchingRemovals(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Path known = fixture.write("new.jpg", "new source");
            Path unreadable = fixture.write("unreadable.jpg", "synthetic");
            fixture.seed(unreadable, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            ReflectionTestUtils.setField(fixture.indexer, "running", true);
            try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
                filesystem.when(() -> Files.walk(directory)).thenReturn(java.util.stream.Stream.of(directory, known, unreadable));
                filesystem.when(() -> Files.readAttributes(unreadable, BasicFileAttributes.class))
                        .thenThrow(new IOException("synthetic stat failure"));

                Object scan = ReflectionTestUtils.invokeMethod(fixture.indexer, "scanDirectoryAgainstDb");

                assertThat((Integer) ReflectionTestUtils.invokeMethod(scan, "changed")).isEqualTo(1);
                assertThat((Integer) ReflectionTestUtils.invokeMethod(scan, "missing")).isZero();
                assertThat(fixture.repository.findByIndexAndNameAndPath(INDEX, known.getFileName().toString(), "")).isPresent();
                assertThat((Boolean) ReflectionTestUtils.invokeMethod(scan, "complete")).isFalse();
            }
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        }
    }

    @Test
    void missingSourceRecoveryUsesTheConfiguredRootInsteadOfAnOldStoredPath(@TempDir Path directory, @TempDir Path oldRoot) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Path source = fixture.write("source.jpg", "synthetic");
            fixture.seed(source, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            Path oldSource = oldRoot.resolve("source.jpg");
            Files.writeString(oldSource, "unwatched source outside the configured root");
            IndexedFile oldIdentity = fixture.file();
            oldIdentity.setBasePath(oldRoot.toString());
            oldIdentity.setFullPath(oldSource.toString());
            fixture.save(oldIdentity);
            Files.delete(source);
            fixture.action = (type, id, completion) -> {
                assertThat(type).isEqualTo(FileIndexerImpl.EventType.DELETE);
                assertThat(fixture.file().getFullPath()).isEqualTo(source.toString());
                completion.success(id);
            };

            fixture.indexer.start(true);

            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.REMOVED);
            assertThat(Files.readString(oldSource)).isEqualTo("unwatched source outside the configured root");
        }
    }

    @Test
    void manualRescanRetriesUnchangedFailedSourcesButScheduledScansDoNot(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            fixture.seed(fixture.write("retry.jpg", "synthetic"), IndexedFile.IndexerStatus.FAILED);
            fixture.indexer.start(true);
            assertThat(fixture.calls.get()).isZero();
            fixture.indexer.rescan(false);
            await(() -> !fixture.indexer.isScanInProgress());
            assertThat(fixture.calls.get()).isZero();

            fixture.rescan();

            assertThat(fixture.calls.get()).isEqualTo(1);
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void changedRescanWaitsForTheActivePathWithoutBlockingOtherPaths(int workers, @TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, workers)) {
            Path file = fixture.write("overlap.jpg", "original");
            fixture.seed(file, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            fixture.indexer.start(true);
            fixture.write("overlap.jpg", "first replacement");
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger active = new AtomicInteger();
            AtomicInteger maxActive = new AtomicInteger();
            fixture.action = (type, id, completion) -> {
                maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                started.countDown();
                assertThat(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                active.decrementAndGet();
                completion.success(id);
            };
            try {
                fixture.dispatch(file, FileIndexerImpl.EventType.MODIFY);
                assertThat(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                fixture.write("overlap.jpg", "second replacement with a new size");
                fixture.indexer.rescan();
                await(() -> fixture.file().getIndexerStatus() == IndexedFile.IndexerStatus.RESCHEDULED);
                assertThat(fixture.file().getSize()).isEqualTo("first replacement".length());
                if (workers > 1) await(() -> !fixture.queuedWork().isEmpty());
                assertThat(fixture.calls.get()).isEqualTo(1);
            } finally {
                release.countDown();
            }
            try {
                await(() -> !fixture.indexer.isScanInProgress());
            } catch (AssertionError failure) {
                throw new AssertionError("calls=" + fixture.calls + " queued=" + fixture.queuedWork()
                        + " locks=" + ReflectionTestUtils.getField(fixture.indexer, "processingLocks")
                        + " events=" + ReflectionTestUtils.getField(fixture.indexer, "pendingEventType")
                        + " scan=" + ReflectionTestUtils.getField(fixture.indexer, "scanInProgress")
                        + " file=" + fixture.file(), failure);
            }
            assertThat(fixture.calls.get()).isEqualTo(2);
            assertThat(maxActive.get()).isEqualTo(1);
            assertThat(fixture.file().getSize()).isEqualTo(Files.size(file));
        }
    }

    @Test
    void startCallbackPreservesARevisionRequestedBeforeTheConsumerStarts(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Path source = fixture.write("start-race.jpg", "original");
            AtomicReference<OnCompletion> callback = new AtomicReference<>();
            FileIndexerObserver observer = (FileIndexerObserver) ReflectionTestUtils.getField(fixture.indexer, "observer");
            doAnswer(call -> {
                callback.set(call.getArgument(2));
                return null;
            }).when(observer).onNewFile(eq(INDEX), anyLong(), any(OnCompletion.class));
            fixture.indexer.start(false);
            await(() -> callback.get() != null);
            fixture.write("start-race.jpg", "new source revision");
            ReflectionTestUtils.invokeMethod(fixture.indexer, "scanDirectoryAgainstDb", true);
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.RESCHEDULED);
            callback.get().started(fixture.file().getId());
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.RESCHEDULED);
            callback.get().success(fixture.file().getId());
            assertThat(fixture.file().getIndexerStatus()).isIn(IndexedFile.IndexerStatus.SCHEDULED, IndexedFile.IndexerStatus.RESCHEDULED);
        }
    }

    @Test
    void unchangedRescanDoesNotReclaimAnActiveCallback(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 2)) {
            fixture.write("active.jpg", "synthetic");
            AtomicReference<OnCompletion> active = new AtomicReference<>();
            fixture.action = (type, id, completion) -> active.set(completion);
            fixture.indexer.start(false);
            await(() -> active.get() != null);
            // Startup owns its latch; exercise the full rescan comparison directly.
            Object scan = ReflectionTestUtils.invokeMethod(fixture.indexer, "scanDirectoryAgainstDb", true);
            assertThat((Integer) ReflectionTestUtils.invokeMethod(scan, "changed")).isZero();
            assertThat(fixture.calls.get()).isEqualTo(1);
            active.get().success(fixture.file().getId());
            await(() -> !fixture.indexer.isScanInProgress());
        }
    }

    @Test
    void staleCompletionCannotFinishAnotherGeneration(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            fixture.write("revision.jpg", "synthetic");
            AtomicReference<OnCompletion> previous = new AtomicReference<>();
            fixture.action = (type, id, completion) -> previous.set(completion);
            fixture.indexer.start(false);
            await(() -> previous.get() != null);
            IndexedFile newer = fixture.file();
            newer.setWorkGeneration(newer.getWorkGeneration() + 1);
            newer.setIndexerStatus(IndexedFile.IndexerStatus.SCHEDULED);
            fixture.save(newer);

            previous.get().success(newer.getId());

            await(() -> !fixture.indexer.isScanInProgress());
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.SCHEDULED);
            assertThat(fixture.file().getIndexerInvocations()).isZero();
        }
    }

    @Test
    void lateStartedAndDuplicateCompletionCannotReviveFinishedWork(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            fixture.write("finished.jpg", "synthetic");
            AtomicReference<OnCompletion> completed = new AtomicReference<>();
            fixture.action = (type, id, completion) -> {
                completed.set(completion);
                completion.success(id);
            };
            fixture.indexer.start(true);
            long id = fixture.file().getId();

            completed.get().started(id);
            completed.get().failed(id, "late failure");
            completed.get().success(id);

            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            assertThat(fixture.file().getIndexerInvocations()).isEqualTo(1);
        }
    }

    @Test
    void completionPersistenceRetriesWithoutReimportingOrReleasingOwnership(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 2)) {
            fixture.write("completion.jpg", "synthetic");
            AtomicBoolean unavailable = new AtomicBoolean(true);
            AtomicInteger rejected = new AtomicInteger();
            doAnswer(invocation -> {
                IndexedFile file = invocation.getArgument(0);
                if (file.getIndexerStatus() == IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS && unavailable.get()) {
                    rejected.incrementAndGet();
                    throw new IllegalStateException("synthetic database outage");
                }
                return fixture.save(file);
            }).when(fixture.repository).save(any(IndexedFile.class));
            fixture.indexer.start(false);
            await(() -> rejected.get() > 0);
            assertThat(fixture.indexer.isScanInProgress()).isTrue();
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.PROCESSING);
            Object scan = ReflectionTestUtils.invokeMethod(fixture.indexer, "scanDirectoryAgainstDb", true);
            assertThat((Integer) ReflectionTestUtils.invokeMethod(scan, "changed")).isZero();

            unavailable.set(false);

            await(() -> !fixture.indexer.isScanInProgress());
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            assertThat(fixture.calls.get()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SCHEDULED", "PROCESSING", "RESCHEDULED"})
    void restartRecoversCommittedImportsWithoutAnActiveOwner(String status, @TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            fixture.seed(fixture.write("orphan.jpg", "synthetic"), IndexedFile.IndexerStatus.valueOf(status));
            fixture.indexer.start(true);
            assertThat(fixture.calls.get()).isEqualTo(1);
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        }
    }

    @Test
    void rescanRecoversOrphanedProcessingWithoutRestart(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            fixture.indexer.start(true);
            fixture.seed(fixture.write("orphan.jpg", "synthetic"), IndexedFile.IndexerStatus.PROCESSING);
            fixture.rescan();
            assertThat(fixture.calls.get()).isEqualTo(1);
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        }
    }

    @Test
    void failedDeletionRetainsItsOperationAndErrorUntilSuccessfulRetry(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Path file = fixture.write("delete.jpg", "synthetic");
            fixture.seed(file, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            fixture.indexer.start(true);
            fixture.action = (type, id, completion) -> {
                assertThat(type).isEqualTo(FileIndexerImpl.EventType.DELETE);
                assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.PROCESSING);
                completion.failed(id, "synthetic cleanup failure");
            };
            Files.delete(file);
            fixture.rescan();
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.FAILED);
            assertThat(fixture.file().getPendingOperation()).isEqualTo(IndexedFile.Operation.DELETE);
            assertThat(fixture.file().getLastMessage()).isEqualTo("Database cleanup failed: synthetic cleanup failure");
            fixture.action = (type, id, completion) -> completion.success(id);

            fixture.rescan();

            assertThat(fixture.calls.get()).isEqualTo(2);
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.REMOVED);
        }
    }

    @Test
    void restoredSourceIsNotDeletedByAnOlderScan(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Path file = fixture.write("restored.jpg", "synthetic");
            fixture.seed(file, IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            fixture.indexer.start(true);
            CountDownLatch release = new CountDownLatch(1);
            fixture.workers().submit(() -> release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Files.delete(file);
            fixture.indexer.rescan();
            await(() -> fixture.workers().getQueue().size() > 0);
            fixture.write("restored.jpg", "synthetic");
            release.countDown();
            await(() -> !fixture.indexer.isScanInProgress());
            assertThat(fixture.calls.get()).isZero();
            assertThat(fixture.file().getIndexerStatus()).isEqualTo(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        }
    }

    @Test
    void queuedScanImportsTheCurrentRevisionInsteadOfDiscoveryAttributes(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, 1)) {
            Path file = fixture.write("upload.jpg", "old");
            CountDownLatch release = new CountDownLatch(1);
            fixture.workers().submit(() -> release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            fixture.indexer.start(false);
            await(() -> fixture.workers().getQueue().size() > 0);
            fixture.write("upload.jpg", "new content after the directory walk");
            release.countDown();
            await(() -> !fixture.indexer.isScanInProgress());
            assertThat(fixture.file().getSize()).isEqualTo(Files.size(file));
            assertThat(fixture.calls.get()).isEqualTo(1);
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private interface Action {
        void process(FileIndexerImpl.EventType type, long id, OnCompletion completion) throws Exception;
    }

    private static final class Fixture implements AutoCloseable {
        final Path directory;
        final IndexerRepository repository = mock(IndexerRepository.class);
        final ConcurrentMap<Long, IndexedFile> stored = new ConcurrentHashMap<>();
        final AtomicLong nextId = new AtomicLong();
        final AtomicInteger calls = new AtomicInteger();
        final FileIndexerImpl indexer;
        volatile Action action = (type, id, completion) -> completion.success(id);

        Fixture(Path directory, int workers) {
            this.directory = directory;
            IndexScanTestQueries.stub(repository, INDEX, stored::values);
            when(repository.findByIndex(INDEX)).thenAnswer(invocation -> stored.values().stream().map(Fixture::copy).toList());
            when(repository.findByIndexAndNameAndPath(eq(INDEX), anyString(), anyString())).thenAnswer(invocation ->
                    stored.values().stream().filter(file -> file.getName().equals(invocation.getArgument(1))
                            && file.getPath().equals(invocation.getArgument(2))).findFirst().map(Fixture::copy));
            when(repository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))).map(Fixture::copy));
            when(repository.saveAndFlush(any(IndexedFile.class))).thenAnswer(invocation -> save(invocation.getArgument(0)));
            when(repository.save(any(IndexedFile.class))).thenAnswer(invocation -> save(invocation.getArgument(0)));
            when(repository.saveAll(any())).thenAnswer(invocation -> {
                Iterable<IndexedFile> files = invocation.getArgument(0);
                java.util.ArrayList<IndexedFile> saved = new java.util.ArrayList<>();
                files.forEach(file -> saved.add(save(file)));
                return saved;
            });
            FileIndexerObserver observer = mock(FileIndexerObserver.class);
            for (FileIndexerImpl.EventType type : FileIndexerImpl.EventType.values()) {
                var stub = doAnswer(invocation -> {
                    long id = invocation.getArgument(1);
                    OnCompletion completion = invocation.getArgument(2);
                    completion.started(id);
                    calls.incrementAndGet();
                    action.process(type, id, completion);
                    return null;
                }).when(observer);
                switch (type) {
                    case CREATE -> stub.onNewFile(eq(INDEX), anyLong(), any(OnCompletion.class));
                    case MODIFY -> stub.onChangedFile(eq(INDEX), anyLong(), any(OnCompletion.class));
                    case DELETE -> stub.onDeletedFile(eq(INDEX), anyLong(), any(OnCompletion.class));
                }
            }
            indexer = new FileIndexerImpl(INDEX, directory, repository, observer, null, workers);
            indexer.setLiveWatchEnabled(false);
        }

        static IndexedFile copy(IndexedFile source) {
            IndexedFile target = new IndexedFile();
            BeanUtils.copyProperties(source, target);
            return target;
        }

        IndexedFile file() { return copy(stored.get(stored.keySet().stream().min(Long::compareTo).orElseThrow())); }
        IndexedFile save(IndexedFile file) {
            if (file.getId() == null) file.setId(nextId.incrementAndGet());
            stored.put(file.getId(), copy(file));
            return file;
        }
        ThreadPoolExecutor workers() { return (ThreadPoolExecutor) ReflectionTestUtils.getField(indexer, "workerPool"); }
        java.util.Map<?, ?> queuedWork() { return (java.util.Map<?, ?>) ReflectionTestUtils.getField(indexer, "queuedWork"); }

        Path write(String name, String content) throws Exception {
            Path path = directory.resolve(name);
            Files.writeString(path, content);
            Files.setLastModifiedTime(path, OLD_TIME);
            return path;
        }

        void seed(Path path, IndexedFile.IndexerStatus status) throws Exception {
            IndexedFile file = new IndexedFile();
            file.setIndex(INDEX);
            file.setName(path.getFileName().toString());
            file.setPath(directory.relativize(path.getParent()).toString());
            file.setBasePath(directory.toString());
            file.setFullPath(path.toString());
            file.setSize(Files.size(path));
            file.setLastModifiedDate(new Date(Files.getLastModifiedTime(path).toMillis()));
            file.setIndexerStatus(status);
            file.setPendingOperation(IndexedFile.Operation.UPSERT);
            save(file);
        }

        void rescan() throws Exception {
            assertThat(indexer.rescan()).isEqualTo(FileIndexerImpl.RescanRequestStatus.STARTED);
            await(() -> !indexer.isScanInProgress());
        }

        void dispatch(Path path, FileIndexerImpl.EventType type) {
            workers().submit(() -> ReflectionTestUtils.invokeMethod(indexer, "processFileOnce", path, type, null, null));
        }

        @Override
        public void close() { indexer.shutdown(); }
    }
}
