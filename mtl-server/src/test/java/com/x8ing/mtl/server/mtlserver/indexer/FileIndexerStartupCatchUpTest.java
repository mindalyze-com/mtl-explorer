package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.event.FileIndexerObserver;
import com.x8ing.mtl.server.mtlserver.indexer.event.OnCompletion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileIndexerStartupCatchUpTest {

    private static final String INDEX = "GPS";
    private static final long AWAIT_MILLIS = 15_000;
    private static final FileTime ORIGINAL_TIME = FileTime.from(Instant.parse("2026-08-01T10:00:00Z"));
    private static final FileTime CHANGED_TIME = FileTime.from(Instant.parse("2026-08-01T11:00:00Z"));

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void catchesAddModifyAndDeleteDuringInitialImport(int workerThreads, @TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, workerThreads)) {
            Path modified = fixture.write("modified.gpx", "<gpx><name>before</name></gpx>");
            Path deleted = fixture.write("deleted.gpx", "<gpx/>");
            fixture.seed(modified);
            fixture.seed(deleted);
            fixture.write("initial.gpx", "<gpx/>");
            fixture.duringInitialImport = () -> {
                assertThat(ReflectionTestUtils.getField(fixture.indexer, "watchService")).isNull();
                assertThat(fixture.indexer.isScanInProgress()).isTrue();
                fixture.write("late.gpx", "<gpx/>");
                Files.writeString(modified, "<gpx><name>after!</name></gpx>");
                Files.setLastModifiedTime(modified, CHANGED_TIME);
                Files.delete(deleted);
            };

            fixture.indexer.start(false);

            await(() -> fixture.events.size() == 4 && !fixture.indexer.isScanInProgress());
            assertThat(fixture.events).containsExactlyInAnyOrder(
                    "CREATE:initial.gpx", "CREATE:late.gpx", "MODIFY:modified.gpx", "DELETE:deleted.gpx");
            assertThat(fixture.scans).hasValue(2);
            assertThat(fixture.find("modified.gpx").getLastModifiedDate())
                    .isEqualTo(Date.from(CHANGED_TIME.toInstant()));
            assertThat(fixture.find("deleted.gpx").getIndexerStatus())
                    .isEqualTo(IndexedFile.IndexerStatus.REMOVED);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void watcherAndCatchUpShareTheSameCreatedFile(int workerThreads, @TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory, workerThreads)) {
            fixture.beforeCatchUp = () -> {
                assertThat(ReflectionTestUtils.getField(fixture.indexer, "watchService")).isNotNull();
                fixture.write("overlap.gpx", "<gpx/>");
            };

            fixture.indexer.start(false);

            await(() -> fixture.events.size() == 1 && !fixture.indexer.isScanInProgress());
            assertThat(fixture.scans).hasValue(2);
            // Writing content/mtime can coalesce CREATE into MODIFY; either imports the new file.
            assertThat(fixture.events).singleElement().asString().endsWith(":overlap.gpx");
            List<String> initialEvents = List.copyOf(fixture.events);
            // A delayed CREATE may arrive after the scan's event was already imported.
            fixture.deliverCreate(directory.resolve("overlap.gpx"));
            assertThat(fixture.events).containsExactlyElementsOf(initialEvents);
            assertThat(fixture.find("overlap.gpx").getIndexerInvocations()).isEqualTo(1);

            fixture.write("live.gpx", "<gpx/>");
            await(() -> fixture.events.size() == 2);
            assertThat(fixture.events).extracting(event -> event.substring(event.indexOf(':') + 1))
                    .containsExactly("overlap.gpx", "live.gpx");
        }
    }

    @Test
    void duplicateCreateSkipsAnUnchangedCompletedFile(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            ReflectionTestUtils.setField(fixture.indexer, "running", true);
            Path file = fixture.write("unchanged.gpx", "<gpx/>");
            fixture.seed(file);

            fixture.deliverCreate(file);

            assertThat(fixture.events).isEmpty();
        }
    }

    @Test
    void createStillProcessesReplacementAndFailedOrRemovedFiles(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            ReflectionTestUtils.setField(fixture.indexer, "running", true);
            Path replacement = fixture.write("replacement.gpx", "<gpx><name>A</name></gpx>");
            Path failed = fixture.write("failed.gpx", "<gpx/>");
            Path removed = fixture.write("removed.gpx", "<gpx/>");
            fixture.seed(replacement);
            fixture.seed(failed).setIndexerStatus(IndexedFile.IndexerStatus.FAILED);
            fixture.seed(removed).setIndexerStatus(IndexedFile.IndexerStatus.REMOVED);
            Files.writeString(replacement, "<gpx><name>B</name></gpx>");
            Files.setLastModifiedTime(replacement, CHANGED_TIME);

            fixture.deliverCreate(replacement);
            fixture.deliverCreate(failed);
            fixture.deliverCreate(removed);

            assertThat(fixture.events).containsExactly(
                    "MODIFY:replacement.gpx", "MODIFY:failed.gpx", "MODIFY:removed.gpx");
        }
    }

    @Test
    void disabledWatcherDoesNotRunHandoverScan(@TempDir Path directory) throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            fixture.indexer.setLiveWatchEnabled(false);
            fixture.indexer.start(true);

            assertThat(fixture.scans).hasValue(1);
            assertThat(ReflectionTestUtils.getField(fixture.indexer, "watchService")).isNull();
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @FunctionalInterface
    private interface FileAction {
        void run() throws Exception;
    }

    private static final class Fixture implements AutoCloseable {
        private final Path directory;
        private final ConcurrentHashMap<Long, IndexedFile> files = new ConcurrentHashMap<>();
        private final AtomicLong ids = new AtomicLong();
        private final AtomicInteger scans = new AtomicInteger();
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final FileIndexerImpl indexer;
        private FileAction duringInitialImport = () -> {
        };
        private FileAction beforeCatchUp = () -> {
        };

        private Fixture(Path directory) {
            this(directory, 1);
        }

        private Fixture(Path directory, int workerThreads) {
            this.directory = directory;
            IndexerRepository repository = mock(IndexerRepository.class);
            IndexScanTestQueries.stub(repository, INDEX, files::values);
            when(repository.findScanEntriesAfterId(eq(INDEX), eq(0L), any())).thenAnswer(invocation -> {
                if (scans.incrementAndGet() == 2) beforeCatchUp.run();
                return files.values().stream().sorted(java.util.Comparator.comparing(IndexedFile::getId)).map(IndexScanTestQueries::entry).toList();
            });
            when(repository.findByIndexAndIndexerStatus(eq(INDEX), any())).thenAnswer(invocation ->
                    files.values().stream().filter(file -> file.getIndexerStatus() == invocation.getArgument(1)).toList());
            when(repository.findByIndexAndNameAndPath(eq(INDEX), anyString(), anyString())).thenAnswer(invocation ->
                    files.values().stream().filter(file -> file.getName().equals(invocation.getArgument(1))
                                                           && file.getPath().equals(invocation.getArgument(2))).findFirst());
            when(repository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(files.get(invocation.getArgument(0))));
            when(repository.saveAndFlush(any(IndexedFile.class))).thenAnswer(invocation -> save(invocation.getArgument(0)));
            when(repository.save(any(IndexedFile.class))).thenAnswer(invocation -> save(invocation.getArgument(0)));
            when(repository.saveAll(any())).thenAnswer(invocation -> {
                Iterable<IndexedFile> discovered = invocation.getArgument(0);
                java.util.ArrayList<IndexedFile> saved = new java.util.ArrayList<>();
                discovered.forEach(file -> saved.add(save(file)));
                return saved;
            });

            FileIndexerObserver observer = mock(FileIndexerObserver.class);
            doAnswer(invocation -> {
                complete(invocation.getArgument(1), invocation.getArgument(2), "CREATE");
                return null;
            }).when(observer).onNewFile(eq(INDEX), anyLong(), any(OnCompletion.class));
            doAnswer(invocation -> {
                complete(invocation.getArgument(1), invocation.getArgument(2), "MODIFY");
                return null;
            }).when(observer).onChangedFile(eq(INDEX), anyLong(), any(OnCompletion.class));
            doAnswer(invocation -> {
                complete(invocation.getArgument(1), invocation.getArgument(2), "DELETE");
                return null;
            }).when(observer).onDeletedFile(eq(INDEX), anyLong(), any(OnCompletion.class));
            indexer = new FileIndexerImpl(INDEX, directory, repository, observer, null, workerThreads);
        }

        private void complete(long id, OnCompletion completion, String event) throws Exception {
            IndexedFile file = files.get(id);
            completion.started(id);
            if (file.getName().equals("initial.gpx")) duringInitialImport.run();
            completion.success(id);
            events.add(event + ":" + file.getName());
        }

        private Path write(String name, String content) throws Exception {
            Path file = directory.resolve(name);
            Files.writeString(file, content);
            Files.setLastModifiedTime(file, ORIGINAL_TIME);
            return file;
        }

        private IndexedFile seed(Path path) throws Exception {
            IndexedFile file = new IndexedFile();
            file.setIndex(INDEX);
            file.setName(path.getFileName().toString());
            file.setPath("");
            file.setBasePath(directory.toString());
            file.setFullPath(path.toString());
            file.setSize(Files.size(path));
            file.setLastModifiedDate(Date.from(Files.getLastModifiedTime(path).toInstant()));
            file.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            file.setWorkGeneration(1);
            file.setIndexerInvocations(1);
            return save(file);
        }

        private IndexedFile save(IndexedFile file) {
            if (file.getId() == null) file.setId(ids.incrementAndGet());
            files.put(file.getId(), file);
            return file;
        }

        private IndexedFile find(String name) {
            return files.values().stream().filter(file -> file.getName().equals(name)).findFirst().orElseThrow();
        }

        private void deliverCreate(Path path) throws Exception {
            ReflectionTestUtils.invokeMethod(indexer, "onFsEvent", path, FileIndexerImpl.EventType.CREATE);
            ExecutorService discovery = (ExecutorService) ReflectionTestUtils.getField(indexer, "discoveryExecutor");
            discovery.submit(() -> {
                java.util.Map<?, ?> sequences = (java.util.Map<?, ?>) ReflectionTestUtils.getField(indexer, "debounceSeq");
                Long token = (Long) sequences.get(path);
                if (token != null) ReflectionTestUtils.invokeMethod(indexer, "processDebounced", path, token);
            }).get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
            await(() -> !indexer.isScanInProgress());
        }

        @Override
        public void close() {
            indexer.shutdown();
        }
    }
}
