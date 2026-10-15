package com.x8ing.mtl.server.mtlserver.indexer;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexedFileScanEntry;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.event.FileIndexerObserver;
import com.x8ing.mtl.server.mtlserver.indexer.event.OnCompletion;
import com.x8ing.mtl.server.mtlserver.utils.ThreadFactories;
import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
@JsonPropertyOrder({
        "index",
        "watchDirectory",
        "repo",
        "observer",
        "txRequiresNew",
        "workStartedListener",
        "running",
        "shuttingDown",
        "watchService",
        "watcherThread",
        "bootThread",
        "scheduler",
        "scanExecutor",
        "runtime",
        "debounceTasks",
        "debounceSeq",
        "pendingEventType",
        "processingLocks",
        "rescanInProgress",
        "scanInProgress",
        "inclusionMatchers",
        "exclusionMatchers",
        "changeDetectionStrategy",
        "liveWatchEnabled",
        "DEBOUNCE_SILENCE",
        "PROBE_INTERVAL",
        "REQUIRED_STABLE_PROBES",
        "STABILITY_RETRY_ATTEMPTS",
        "STABILITY_RETRY_DELAY"
})
public class FileIndexerImpl {

    public static final int DEFAULT_WORKER_THREADS = 2;

    private static final int MIN_WORKER_THREADS = 1;
    private static final String NON_REGULAR_SOURCE_MESSAGE = "Source path is not a regular file";
    private static final int WALK_PROGRESS_LOG_INTERVAL = 1_000;
    private static final int SCAN_LOOKUP_BATCH_SIZE = 256;
    private static final int SCAN_WORK_BATCH_SIZE = 128;
    private static final List<IndexedFile.IndexerStatus> RECOVERABLE_STATUSES = List.of(
            IndexedFile.IndexerStatus.SCHEDULED, IndexedFile.IndexerStatus.PROCESSING, IndexedFile.IndexerStatus.RESCHEDULED);
    private static final int WORK_STATE_LOCK_STRIPES = 64;
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String DEADLOCK_SQL_STATE = "40P01";
    private static final String SERIALIZATION_FAILURE_SQL_STATE = "40001";
    private static final int COMPLETION_MAX_RETRIES = 3;
    private static final long COMPLETION_RETRY_BASE_DELAY_MS = 25L;
    private static final int COMPLETION_PERSISTENCE_RETRY_ATTEMPTS = 8;
    private static final long COMPLETION_PERSISTENCE_RETRY_BASE_MS = 250L;
    private static final long COMPLETION_PERSISTENCE_RETRY_MAX_MS = 5_000L;
    private static final int SHUTDOWN_CONTROL_TIMEOUT_SECONDS = 10;
    private static final int SHUTDOWN_WORKER_TIMEOUT_SECONDS = 30;

    // ---- ctor args / collaborators
    private final String index;
    private final Path watchDirectory;
    private final IndexerRepository repo;
    private final FileIndexerObserver observer;
    private final TransactionTemplate txRequiresNew;

    // ---- runtime
    private Runnable workStartedListener = () -> {
    };
    private volatile boolean running = false;
    private volatile boolean shuttingDown = false;
    private WatchService watchService;
    private volatile Thread watcherThread;
    private volatile Thread bootThread;

    // per-path debounce task (silence-based)
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, ThreadFactories.namedDaemon("idx-deb"));
    private final ExecutorService scanExecutor =
            Executors.newSingleThreadExecutor(ThreadFactories.namedDaemon("idx-scan"));
    private final ExecutorService discoveryExecutor =
            Executors.newSingleThreadExecutor(ThreadFactories.namedDaemon("idx-discover"));
    private final int workerThreads;
    private final ExecutorService workerPool;

    // debounce state
    private final Map<Path, ScheduledFuture<?>> debounceTasks = new ConcurrentHashMap<>();
    private final Map<Path, Long> debounceSeq = new ConcurrentHashMap<>();           // sequence token per path
    private final Map<Path, EventType> pendingEventType = new ConcurrentHashMap<>(); // coalesced type per path

    // prevent concurrent process of same path
    private final Set<Path> processingLocks = ConcurrentHashMap.newKeySet();
    private final Map<Path, QueuedWork> queuedWork = new ConcurrentHashMap<>();

    private record BatchCompletion(CountDownLatch latch, AtomicInteger pending) {
        void complete() {
            completeInitialWork(latch, pending);
        }
    }

    private record QueuedWork(EventType type, List<BatchCompletion> completions) {
        QueuedWork merge(QueuedWork newer) {
            List<BatchCompletion> merged = new ArrayList<>(completions);
            merged.addAll(newer.completions);
            return new QueuedWork(newer.type, List.copyOf(merged));
        }
    }

    private enum CompletionOutcome {SUCCESS, FAILURE, SOURCE_FAILURE, SKIPPED, SOURCE_CHANGED}

    // guard against concurrent overflow rescans
    private final AtomicBoolean rescanInProgress = new AtomicBoolean(false);

    // guard for both initial scan and periodic rescans
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);
    private final AtomicBoolean discoveryInProgress = new AtomicBoolean(false);
    // Guards debounce bookkeeping only; no database work runs under this monitor.
    private final Object workStateMonitor = new Object();
    private final Object[] pathMonitors = new Object[WORK_STATE_LOCK_STRIPES];

    // include/exclude
    private final List<PathMatcher> inclusionMatchers = new CopyOnWriteArrayList<>();
    private final List<PathMatcher> exclusionMatchers = new CopyOnWriteArrayList<>();

    // change-detection strategy
    public enum ChangeDetectionStrategy {SIZE_ONLY, SIZE_AND_MTIME}

    public enum RescanRequestStatus {STARTED, ALREADY_RUNNING, NOT_RUNNING}

    private ChangeDetectionStrategy changeDetectionStrategy = ChangeDetectionStrategy.SIZE_AND_MTIME;

    // when false: initial scan + periodic rescans run, but no inotify WatchService is registered
    private boolean liveWatchEnabled = true;

    // config knobs
    private final Duration DEBOUNCE_SILENCE = Duration.ofSeconds(8);
    private final Duration PROBE_INTERVAL = Duration.ofSeconds(1);
    private final int REQUIRED_STABLE_PROBES = 2;
    private final int STABILITY_RETRY_ATTEMPTS = 10;
    private final Duration STABILITY_RETRY_DELAY = Duration.ofSeconds(5);

    public FileIndexerImpl(String index,
                           Path watchDirectory,
                           IndexerRepository repo,
                           FileIndexerObserver observer,
                           PlatformTransactionManager txManager) {
        this(index, watchDirectory, repo, observer, txManager, DEFAULT_WORKER_THREADS);
    }

    public FileIndexerImpl(String index,
                           Path watchDirectory,
                           IndexerRepository repo,
                           FileIndexerObserver observer,
                           PlatformTransactionManager txManager,
                           int workerThreads) {
        this.index = index;
        this.watchDirectory = watchDirectory.toAbsolutePath().normalize();
        this.repo = repo;
        this.observer = observer;
        Arrays.setAll(pathMonitors, ignored -> new Object());
        this.workerThreads = Math.max(MIN_WORKER_THREADS, workerThreads);
        this.workerPool = Executors.newFixedThreadPool(
                this.workerThreads,
                ThreadFactories.namedDaemon("idx"));

        TransactionTemplate t = null;
        if (txManager != null) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            t = new TransactionTemplate(txManager, def);
        }
        this.txRequiresNew = t;
        log.info("Configured {} worker thread(s) for index={}", this.workerThreads, index);
    }

    // ---------- public config

    public void setInclusionPatterns(List<PathMatcher> m) {
        inclusionMatchers.clear();
        if (m != null) inclusionMatchers.addAll(m);
        log.info("Configured {} inclusion patterns", inclusionMatchers.size());
    }

    public void setExclusionPatterns(List<PathMatcher> m) {
        exclusionMatchers.clear();
        if (m != null) exclusionMatchers.addAll(m);
        log.info("Configured {} exclusion patterns", exclusionMatchers.size());
    }

    public void setChangeDetectionStrategy(ChangeDetectionStrategy strategy) {
        this.changeDetectionStrategy = strategy;
        log.info("Change detection strategy set to {} for index={}", strategy, index);
    }

    public void setLiveWatchEnabled(boolean liveWatchEnabled) {
        this.liveWatchEnabled = liveWatchEnabled;
        log.info("Live watch enabled={} for index={}", liveWatchEnabled, index);
    }

    public void setWorkStartedListener(Runnable workStartedListener) {
        this.workStartedListener = Objects.requireNonNull(workStartedListener);
    }

    public boolean isScanInProgress() {
        return scanInProgress.get() || rescanInProgress.get() || discoveryInProgress.get()
               || !pendingEventType.isEmpty() || !queuedWork.isEmpty() || !processingLocks.isEmpty();
    }

    public boolean isDiscoveryInProgress() {
        return discoveryInProgress.get();
    }

    /**
     * Trigger a full rescan of the watch directory against the DB.
     * Skips if a scan is already running. Safe to call from a scheduler.
     */
    public RescanRequestStatus rescan() {
        return rescan(true);
    }

    public RescanRequestStatus rescan(boolean retryFailed) {
        if (!running) {
            log.warn("rescan() called but indexer is not running for index={}", index);
            return RescanRequestStatus.NOT_RUNNING;
        }
        if (!scanInProgress.compareAndSet(false, true)) {
            log.info("Rescan skipped — scan already in progress for index={}", index);
            return RescanRequestStatus.ALREADY_RUNNING;
        }
        workStartedListener.run();
        scanExecutor.submit(() -> {
            try {
                log.info("Rescan starting for index={}", index);
                performInitialScanAndRecovery(retryFailed);
                log.info("Rescan completed for index={}", index);
            } catch (Exception e) {
                log.error("Rescan failed for index={}", index, e);
            } finally {
                scanInProgress.set(false);
            }
        });
        return RescanRequestStatus.STARTED;
    }

    private boolean isIncluded(Path p) {
        return inclusionMatchers.isEmpty() || matchesAny(p, inclusionMatchers);
    }

    private boolean isExcluded(Path p) {
        return matchesAny(p, exclusionMatchers);
    }

    private boolean matchesAny(Path path, List<PathMatcher> matchers) {
        if (matchers.isEmpty()) return false;
        Path abs = path.toAbsolutePath().normalize();
        Path name = abs.getFileName();
        for (PathMatcher matcher : matchers) {
            try {
                if (matcher.matches(abs) || (name != null && matcher.matches(name))) return true;
            } catch (Exception ignore) {
            }
        }
        return false;
    }

    // ---------- lifecycle

    public void start(boolean blocking) {
        synchronized (this) {
            if (shuttingDown) return;
            if (running) {
                log.warn("Already running for index={}", index);
                return;
            }
            if (!Files.isDirectory(watchDirectory)) {
                throw new IllegalStateException("watchDirectory must exist: " + watchDirectory);
            }
            running = true;
            scanInProgress.set(true);
            workStartedListener.run();
            bootThread = blocking ? Thread.currentThread() : new Thread(this::bootInternal, "idx-boot-" + index);
            if (!blocking) {
                bootThread.setDaemon(true);
                bootThread.start();
                return;
            }
        }
        if (blocking) {
            bootInternal(); // run scan+recovery+watch on this thread
            if (watcherThread != null) {
                try {
                    watcherThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void bootInternal() {
        scanInProgress.set(true);
        try {
            if (!running) return;
            // Initial batch (scan + recovery + latch await)
            performInitialScanAndRecovery(false);
            // Live watcher (may be disabled for indexes with very large directory trees)
            if (liveWatchEnabled && running) {
                startWatcher();
                if (running) {
                    catchUpAfterWatcherStart();
                }
            } else if (!liveWatchEnabled && running) {
                log.info("Live file watching disabled for index={} - relying on periodic rescans only", index);
            }
        } catch (Throwable t) {
            if (shuttingDown) {
                log.warn("Boot sequence stopped during shutdown for index={}: type={} message={}", index,
                        t.getClass().getSimpleName(), t.getMessage());
                log.debug("Boot sequence shutdown detail for index={}", index, t);
            } else log.error("Boot sequence failed for index={}", index, t);
            running = false;
        } finally {
            scanInProgress.set(false);
        }
    }

    private synchronized void startWatcher() {
        if (!running || shuttingDown) return;
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerAll(watchDirectory);
            log.info("Live watcher started at {}", watchDirectory);
        } catch (Exception e) {
            log.error("WatchService init failed", e);
            running = false;
            return;
        }

        watcherThread = new Thread(this::watchLoopSafe, "watch-" + index);
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private void catchUpAfterWatcherStart() {
        long start = System.nanoTime();
        log.info("Startup catch-up scan starting for index={}", index);
        // Keep the watcher off during initial imports, then reconcile the unobserved
        // interval after registration. Scan results share the normal live-event queue.
        DirScanResult scan = scanDirectoryAgainstDb();
        dispatchCommittedWork();
        log.info("Startup catch-up scan completed for index={}: new/changed={} missing={} durationMs={}; changes queued for live processing",
                index, scan.changed(), scan.missing(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }

    /**
     * Signal all indexer loops without waiting; active file callbacks can finish their transaction.
     */
    public void requestStop() {
        synchronized (this) {
            if (shuttingDown) return;
            shuttingDown = true;
            running = false;
        }
        log.info("Stopping indexer {}", index);
        if (bootThread != null && bootThread != Thread.currentThread()) bootThread.interrupt();

        // Cancel all pending debounce tasks
        synchronized (workStateMonitor) {
            debounceTasks.values().forEach(f -> f.cancel(false));
            debounceTasks.clear();
            debounceSeq.clear();
            pendingEventType.clear();
        }

        try {
            if (watchService != null) watchService.close();
        } catch (Exception exception) {
            log.warn("WatchService close failed for index={}: type={} message={}", index,
                    exception.getClass().getSimpleName(), exception.getMessage());
            log.debug("WatchService close failure for index={}", index, exception);
        }

        scheduler.shutdownNow();
        scanExecutor.shutdownNow();
        discoveryExecutor.shutdownNow();
        workerPool.shutdown();
    }

    public void shutdown() {
        long started = System.nanoTime();
        requestStop();
        try {
            if (!workerPool.awaitTermination(SHUTDOWN_WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Indexer worker shutdown timed out for index={}; interrupting active work", index);
                workerPool.shutdownNow();
                if (!workerPool.awaitTermination(SHUTDOWN_WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("Indexer workers still active after interruption for index={}", index);
                }
            }
            scheduler.awaitTermination(SHUTDOWN_CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            scanExecutor.awaitTermination(SHUTDOWN_CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            discoveryExecutor.awaitTermination(SHUTDOWN_CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            joinIndexerThread(bootThread);
            joinIndexerThread(watcherThread);
        } catch (InterruptedException e) {
            log.warn("Indexer shutdown interrupted for index={}: type={} message={}", index,
                    e.getClass().getSimpleName(), e.getMessage());
            scheduler.shutdownNow();
            scanExecutor.shutdownNow();
            discoveryExecutor.shutdownNow();
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("Indexer shutdown completed: index={} workersTerminated={} durationMs={}", index,
                workerPool.isTerminated(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    private void joinIndexerThread(Thread thread) throws InterruptedException {
        if (thread == null || thread == Thread.currentThread()) return;
        thread.join(TimeUnit.SECONDS.toMillis(SHUTDOWN_CONTROL_TIMEOUT_SECONDS));
        if (thread.isAlive()) log.warn("Indexer thread still active after shutdown: index={} thread={}", index, thread.getName());
    }

    // ---------- initial batch (two-phase + latch + recovery)

    @JsonPropertyOrder({
            "path",
            "attrs"
    })
    private record FileToProcess(Path path, BasicFileAttributes attrs) {
    }

    @JsonPropertyOrder({
            "changed",
            "missing",
            "complete"
    })
    private record DirScanResult(int changed, int missing, boolean complete) {
    }

    enum EventType {CREATE, MODIFY, DELETE}

    /**
     * Compare bounded groups of source revisions with narrow database projections.
     * Work is committed durably; the result retains counts rather than source lists.
     */
    private DirScanResult scanDirectoryAgainstDb() {
        return scanDirectoryAgainstDb(false);
    }

    DirScanResult scanDirectoryAgainstDb(boolean retryFailed) {
        discoveryInProgress.set(true);
        try {
            return discoverAndCommitSources(retryFailed);
        } finally {
            discoveryInProgress.set(false);
        }
    }

    private DirScanResult discoverAndCommitSources(boolean retryFailed) {
        List<FileToProcess> batch = new ArrayList<>(SCAN_LOOKUP_BATCH_SIZE);
        AtomicInteger changed = new AtomicInteger();
        AtomicInteger examined = new AtomicInteger();
        AtomicInteger statFailures = new AtomicInteger();
        AtomicInteger discoveryFailures = new AtomicInteger();
        log.info("Directory walk starting for index={} path={}", index, watchDirectory);
        try {
            SourceFileRevision.requireAvailableRoot(watchDirectory);
            try (var paths = Files.walk(watchDirectory)) {
                paths.takeWhile(p -> running && !Thread.currentThread().isInterrupted()).forEach(path -> {
                    try {
                        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                        if (!attributes.isRegularFile() || !isIncluded(path) || isExcluded(path)) return;
                        Path normalized = normalize(path);
                        batch.add(new FileToProcess(normalized, attributes));
                        if (batch.size() >= SCAN_LOOKUP_BATCH_SIZE) compareDiscoveredSources(batch, retryFailed, changed, discoveryFailures);
                        recordWalkProgress(examined, changed.get());
                    } catch (IOException exception) {
                        if (statFailures.incrementAndGet() == 1) {
                            log.warn("Source stat unavailable: index={} path={} type={} message={}; removals will be skipped",
                                    index, path, exception.getClass().getSimpleName(), exception.getMessage());
                        }
                        log.debug("Source stat failure: index={} path={}", index, path, exception);
                    }
                });
            }
        } catch (IOException | UncheckedIOException exception) {
            if (!running || Thread.currentThread().isInterrupted()) return new DirScanResult(changed.get(), 0, false);
            statFailures.incrementAndGet();
            log.warn("Source walk incomplete: index={} path={} type={} message={}; preserving missing records",
                    index, watchDirectory, exception.getClass().getSimpleName(), exception.getMessage());
            log.debug("Incomplete source walk", exception);
        }
        if (!running || Thread.currentThread().isInterrupted()) {
            log.info("Directory walk stopped for index={}: filesExamined={}; partial scan discarded", index, examined.get());
            return new DirScanResult(changed.get(), 0, false);
        }
        compareDiscoveredSources(batch, retryFailed, changed, discoveryFailures);
        boolean complete = statFailures.get() == 0;
        int missing = complete ? discoverMissingSources(statFailures, discoveryFailures) : 0;
        complete &= statFailures.get() == 0 && running && !Thread.currentThread().isInterrupted();
        log.info("Directory walk finished for index={}: complete={} filesExamined={} queued={} missing={} statFailures={} discoveryFailures={}",
                index, complete, examined.get(), changed.get(), missing, statFailures.get(), discoveryFailures.get());
        return new DirScanResult(changed.get(), missing, complete);
    }

    private void compareDiscoveredSources(List<FileToProcess> sources, boolean retryFailed, AtomicInteger changed, AtomicInteger failures) {
        Map<String, List<FileToProcess>> directories = new LinkedHashMap<>();
        for (FileToProcess source : sources) {
            directories.computeIfAbsent(rel(watchDirectory, source.path().getParent()), ignored -> new ArrayList<>()).add(source);
        }
        for (var directory : directories.entrySet()) {
            if (!running || Thread.currentThread().isInterrupted()) break;
            List<String> names = directory.getValue().stream().map(source -> source.path().getFileName().toString()).toList();
            Map<String, IndexedFileScanEntry> existing = new HashMap<>();
            for (IndexedFileScanEntry entry : repo.findScanEntries(index, directory.getKey(), names)) existing.put(entry.name(), entry);
            for (FileToProcess source : directory.getValue()) {
                if (!running || Thread.currentThread().isInterrupted()) break;
                IndexedFileScanEntry prior = existing.get(source.path().getFileName().toString());
                if (prior != null && isFileUnchanged(prior.size(), prior.lastModifiedDate(), source.attrs())) {
                    boolean active = processingLocks.contains(source.path());
                    if (prior.indexerStatus() != IndexedFile.IndexerStatus.RESCHEDULED
                        && (prior.indexerStatus() == IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS
                            || active && (prior.indexerStatus() == IndexedFile.IndexerStatus.SCHEDULED
                                          || prior.indexerStatus() == IndexedFile.IndexerStatus.PROCESSING)
                            || !retryFailed && prior.indexerStatus() == IndexedFile.IndexerStatus.FAILED)) continue;
                }
                commitDiscoveredSource(source, changed, failures);
            }
        }
        sources.clear();
    }

    private int discoverMissingSources(AtomicInteger statFailures, AtomicInteger failures) {
        int missing = 0;
        long afterId = 0;
        while (running && !Thread.currentThread().isInterrupted()) {
            List<IndexedFileScanEntry> entries = repo.findScanEntriesAfterId(index, afterId, PageRequest.of(0, SCAN_LOOKUP_BATCH_SIZE));
            if (entries.isEmpty()) break;
            afterId = entries.getLast().id();
            for (IndexedFileScanEntry entry : entries) {
                if (!running || Thread.currentThread().isInterrupted()) return missing;
                if (entry.indexerStatus() == IndexedFile.IndexerStatus.EXCLUDED || entry.indexerStatus() == IndexedFile.IndexerStatus.REMOVED) continue;
                Path path = scanPath(entry);
                if (path == null) continue;
                try {
                    BasicFileAttributes attributes = SourceFileRevision.readAttributes(watchDirectory, path);
                    if (attributes != null) {
                        if (!attributes.isRegularFile()) registerDiscoveredWork(path, attributes);
                        continue;
                    }
                    // Recheck under the path lock: a source may have reappeared since this page was read.
                    synchronized (monitorFor(path)) {
                        registerDiscoveredWork(path, SourceFileRevision.readAttributes(watchDirectory, path));
                    }
                    missing++;
                } catch (IOException | UncheckedIOException exception) {
                    statFailures.incrementAndGet();
                    log.warn("Source removal scan incomplete: index={} path={} type={} message={}; remaining removals skipped",
                            index, path, exception.getClass().getSimpleName(), exception.getMessage());
                    log.debug("Incomplete source removal scan", exception);
                    return missing;
                } catch (RuntimeException exception) {
                    if (!isPersistenceFailure(exception)) throw exception;
                    recordDiscoveryFailure(path, exception, failures);
                }
            }
        }
        return missing;
    }

    private void commitDiscoveredSource(FileToProcess source, AtomicInteger committed, AtomicInteger failures) {
        try {
            // Reloads under the path lock: a watcher may have committed this path since the scan snapshot.
            registerDiscoveredWork(source.path(), source.attrs());
            committed.incrementAndGet();
        } catch (RuntimeException exception) {
            if (!isPersistenceFailure(exception)) throw exception;
            recordDiscoveryFailure(source.path(), exception, failures);
        }
    }

    private void recordDiscoveryFailure(Path path, RuntimeException exception, AtomicInteger failures) {
        if (failures.incrementAndGet() == 1) {
            log.warn("Discovery commit failed: index={} path={} type={} message={}; source left for next scan",
                    index, path, exception.getClass().getSimpleName(), exception.getMessage());
        }
        log.debug("Discovery commit failure: index={} path={}", index, path, exception);
    }

    private void recordWalkProgress(AtomicInteger examined, int queued) {
        int count = examined.incrementAndGet();
        if (count % WALK_PROGRESS_LOG_INTERVAL == 0) {
            log.info("Walk progress index={}: {} files examined, {} queued for processing", index, count, queued);
        }
    }

    // ---------- initial batch (two-phase + latch + recovery)
    private void performInitialScanAndRecovery(boolean retryFailed) {
        long started = System.nanoTime();
        log.info("Starting scan of {} (retryFailed={})", watchDirectory, retryFailed);
        // Commit the entire backlog before dispatch, then recover it in bounded pages.
        DirScanResult scan = scanDirectoryAgainstDb(retryFailed);
        if (!running) return;
        int total = scan.changed() + scan.missing();
        if (!dispatchCommittedWork()) return;
        if (scan.complete()) {
            log.info("Scan completed for index={}: callbacks={} durationMs={}", index, total,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } else {
            log.warn("Scan finished incompletely for index={}: knownChangeCallbacks={} durationMs={}; removals skipped until storage is available",
                    index, total, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    private boolean dispatchCommittedWork() {
        long afterId = 0;
        while (running && !Thread.currentThread().isInterrupted()) {
            List<IndexedFileScanEntry> entries = repo.findPendingScanEntriesAfterId(index, afterId, RECOVERABLE_STATUSES,
                    PageRequest.of(0, SCAN_WORK_BATCH_SIZE));
            if (entries.isEmpty()) return true;
            afterId = entries.getLast().id();
            AtomicInteger pending = new AtomicInteger(entries.size());
            CountDownLatch latch = new CountDownLatch(entries.size());
            for (IndexedFileScanEntry entry : entries) {
                if (!running || Thread.currentThread().isInterrupted()) return false;
                Path path = scanPath(entry);
                if (path == null || processingLocks.contains(path) && entry.indexerStatus() != IndexedFile.IndexerStatus.RESCHEDULED) {
                    completeInitialWork(latch, pending);
                    continue;
                }
                // CREATE is reconciled with the durable generation and current filesystem truth.
                queueWork(path, EventType.CREATE, latch, pending);
            }
            if (!awaitScanCallbacks(latch, pending)) return false;
        }
        return false;
    }

    private boolean awaitScanCallbacks(CountDownLatch latch, AtomicInteger pending) {
        try {
            if (!latch.await(48, TimeUnit.HOURS)) {
                log.warn("Scan timeout for index={}; {} callbacks still pending", index, pending.get());
                return false;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Scan wait interrupted for index={}; {} callbacks still pending", index, pending.get());
            return false;
        }
        return true;
    }

    // ---------- live watch (silence-based debounce; plain WatchService)

    private void watchLoopSafe() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }
            Path dir = (Path) key.watchable();
            for (WatchEvent<?> evt : key.pollEvents()) {
                if (!running || Thread.currentThread().isInterrupted()) return;
                if (evt.kind() == StandardWatchEventKinds.OVERFLOW) {
                    log.warn("WatchService OVERFLOW for index={} — events may have been lost, scheduling rescan", index);
                    scanExecutor.submit(this::rescanAfterOverflow);
                    continue;
                }
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) evt;
                Path child = normalize(dir.resolve(ev.context()));

                // Register subdirs
                if (evt.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                    try {
                        registerAll(child);
                    } catch (Exception ignore) {
                    }
                }

                EventType type = mapKind(evt.kind());
                onFsEvent(child, type);
            }
            key.reset();
        }
        log.info("Watch loop terminated for {}", index);
    }

    private void onFsEvent(Path path, EventType type) {
        Path k = normalize(path);
        if (!isIncluded(k) || isExcluded(k)) {
            log.debug("Live watcher ignored non-indexable path: {}", k.getFileName());
            return;
        }
        synchronized (workStateMonitor) {
            if (!running) return;
            // Pending memory state blocks dependent jobs while the asynchronous
            // discovery transaction is committing. Later events coalesce here.
            boolean firstInWindow = pendingEventType.put(k, type) == null;
            if (firstInWindow) {
                submitDiscovery(k, () -> {
                    if (!running) return;
                    try {
                        registerDiscoveredWork(k, SourceFileRevision.readAttributes(watchDirectory, k));
                    } catch (IOException | RuntimeException exception) {
                        log.warn("Watcher discovery commit failed for index={} file={}: type={} message={}; retrying before import",
                                index, k.getFileName(), exception.getClass().getSimpleName(), exception.getMessage());
                        log.debug("Watcher discovery commit failure", exception);
                    }
                });
            }
            ScheduledFuture<?> previous = debounceTasks.get(k);
            if (previous != null) previous.cancel(false);
            else log.info("Live watcher detected {} for: {}", type, k.getFileName());

            long token = debounceSeq.merge(k, 1L, Long::sum);
            AtomicReference<ScheduledFuture<?>> scheduledTask = new AtomicReference<>();
            Runnable trigger = () -> {
                ScheduledFuture<?> ownTask = scheduledTask.get();
                if (ownTask != null) debounceTasks.remove(k, ownTask);
                if (Objects.equals(debounceSeq.get(k), token)) submitDiscovery(k, () -> processDebounced(k, token));
            };
            try {
                ScheduledFuture<?> future = scheduler.schedule(trigger, DEBOUNCE_SILENCE.toMillis(), TimeUnit.MILLISECONDS);
                scheduledTask.set(future);
                debounceTasks.put(k, future);
            } catch (RejectedExecutionException exception) {
                log.warn("Watcher debounce stopped for index={} file={}: {}", index, k.getFileName(), exception.toString());
                pendingEventType.remove(k, type);
                debounceSeq.remove(k, token);
            }
        }
    }

    private void submitDiscovery(Path path, Runnable task) {
        try {
            discoveryExecutor.execute(task);
        } catch (RejectedExecutionException exception) {
            log.warn("Discovery dispatch stopped for index={} file={}: {}; committed work remains recoverable",
                    index, path.getFileName(), exception.toString());
        }
    }

    /**
     * Loop-on-release pattern: process all pending events for this path,
     * continuing to drain any that arrive during processing (e.g., during
     * waitUntilStable). This prevents events from being silently dropped
     * when a file is modified while already being processed.
     */
    private void processDebounced(Path path, long triggerToken) {
        EventType type;
        synchronized (workStateMonitor) {
            if (!Objects.equals(debounceSeq.get(path), triggerToken)) return;
            type = pendingEventType.get(path);
        }
        if (type != null && running) {
            try {
                registerDiscoveredWork(path, SourceFileRevision.readAttributes(watchDirectory, path));
                queueWork(path, type, null, null);
            } catch (IOException | RuntimeException exception) {
                log.warn("Live discovery commit failed for index={} file={}: type={} message={}; retrying before dispatch",
                        index, path.getFileName(), exception.getClass().getSimpleName(), exception.getMessage());
                log.debug("Live discovery commit failure", exception);
                try {
                    scheduler.schedule(() -> {
                        if (Objects.equals(debounceSeq.get(path), triggerToken)) onFsEvent(path, pendingEventType.getOrDefault(path, type));
                    }, COMPLETION_PERSISTENCE_RETRY_MAX_MS, TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException stopped) {
                    log.warn("Live discovery retry stopped for index={} file={}: {}", index, path.getFileName(), stopped.toString());
                }
                return;
            }
        }
        synchronized (workStateMonitor) {
            if (Objects.equals(debounceSeq.get(path), triggerToken)) {
                if (type != null) pendingEventType.remove(path, type);
                debounceSeq.remove(path, triggerToken);
            }
        }
    }

    /**
     * Full directory rescan after WatchService OVERFLOW — walks the directory,
     * compares files against DB state (size/mtime), and feeds changed files
     * through the normal event pipeline.
     */
    private void rescanAfterOverflow() {
        if (!rescanInProgress.compareAndSet(false, true)) {
            log.info("Overflow rescan already in progress for index={}, skipping", index);
            return;
        }
        try {
            workStartedListener.run();
            log.warn("Starting overflow rescan for index={}", index);

            // Re-register all directories (new subdirs may have been missed during overflow)
            registerAll(watchDirectory);

            DirScanResult scan = scanDirectoryAgainstDb();
            dispatchCommittedWork();

            log.info("Overflow rescan completed for index={}: changed={} missing={}",
                    index, scan.changed(), scan.missing());
        } catch (Exception e) {
            log.error("Overflow rescan failed for index={}", index, e);
        } finally {
            rescanInProgress.set(false);
        }
    }

    private BasicFileAttributes waitUntilStable(Path p) throws Exception {
        BasicFileAttributes a1 = SourceFileRevision.readAttributes(watchDirectory, p);
        if (a1 == null) return null;
        if (!a1.isRegularFile()) return a1;

        // Old readable sources avoid a per-file delay on large scans. Recent/future
        // timestamps still require stable probes; consumers validate this revision
        // again inside their transaction before publishing its domain rows.
        Duration observationWindow = PROBE_INTERVAL.multipliedBy(REQUIRED_STABLE_PROBES);
        if (stableObservationWindowElapsed(a1.lastModifiedTime(), System.currentTimeMillis(), observationWindow)
            && hasExpectedReadableSize(p, a1.size())) {
            return a1;
        }

        int attempts = 0;
        while (attempts <= STABILITY_RETRY_ATTEMPTS) {
            if (!running || Thread.currentThread().isInterrupted()) throw new InterruptedException("Indexer is stopping");
            boolean stable = true;
            BasicFileAttributes prev = a1;
            for (int i = 0; i < REQUIRED_STABLE_PROBES; i++) {
                if (!running || Thread.currentThread().isInterrupted()) throw new InterruptedException("Indexer is stopping");
                Thread.sleep(PROBE_INTERVAL.toMillis());
                BasicFileAttributes a2 = SourceFileRevision.readAttributes(watchDirectory, p);
                if (a2 == null) return null;
                if (prev.size() != a2.size() || !prev.lastModifiedTime().equals(a2.lastModifiedTime())) {
                    stable = false;
                    a1 = a2;
                    break;
                }
                prev = a2;
            }
            if (stable) {
                try (SeekableByteChannel ch = Files.newByteChannel(p, StandardOpenOption.READ)) {
                    if (ch.size() != prev.size()) {
                        stable = false;
                    }
                } catch (IOException e) {
                    // File locked by another process (e.g. FTP upload, OS copy) — treat as not stable
                    log.debug("File {} locked during stability check: {}", p.getFileName(), e.getMessage());
                    stable = false;
                }
            }
            if (stable) return prev;

            attempts++;
            log.info("File {} not stable yet; retry {}/{}", p.getFileName(), attempts, STABILITY_RETRY_ATTEMPTS);
            Thread.sleep(STABILITY_RETRY_DELAY.toMillis());
        }

        // Final failure after all retries exhausted
        log.warn("AUDIT: File failed stability check after {} attempts | file={} | reason=File continued changing during observation period",
                STABILITY_RETRY_ATTEMPTS, p.getFileName());
        throw new IllegalStateException("Failed stability check after " + STABILITY_RETRY_ATTEMPTS + " attempts for " + p.getFileName());
    }

    static boolean stableObservationWindowElapsed(FileTime lastModifiedTime,
                                                  long currentTimeMillis,
                                                  Duration observationWindow) {
        long lastModifiedMillis = lastModifiedTime.toMillis();
        return lastModifiedMillis <= currentTimeMillis
               && currentTimeMillis - lastModifiedMillis >= observationWindow.toMillis();
    }

    private static boolean hasExpectedReadableSize(Path path, long expectedSize) {
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            return channel.size() == expectedSize;
        } catch (IOException e) {
            return false;
        }
    }

    // ---------- core processing (single place)

    private void processFileOnce(Path file, EventType type,
                                 CountDownLatch latch, AtomicInteger pending) {
        queueWork(normalize(file), type, latch, pending);
    }

    private void queueWork(Path path, EventType type, CountDownLatch latch, AtomicInteger pending) {
        List<BatchCompletion> completions = latch == null ? List.of() : List.of(new BatchCompletion(latch, pending));
        queuedWork.merge(path, new QueuedWork(type, completions), QueuedWork::merge);
        workerPool.submit(() -> processPendingWork(path));
    }

    private void processPendingWork(Path path) {
        QueuedWork work;
        synchronized (monitorFor(path)) {
            if (!processingLocks.add(path)) return;
            work = queuedWork.remove(path);
        }
        if (work == null) {
            releasePath(path);
            return;
        }
        AtomicBoolean finished = new AtomicBoolean();
        Runnable finish = () -> {
            if (!finished.compareAndSet(false, true)) return;
            work.completions().forEach(BatchCompletion::complete);
            releasePath(path);
        };
        if (!running) {
            finish.run();
            return;
        }
        try {
            // Discovery attributes can be hours old. Read the current stable revision
            // inside this path's ownership, immediately before scheduling its import.
            BasicFileAttributes attributes = waitUntilStable(path);
            if (attributes != null && !attributes.isRegularFile()) {
                markNonRegularSource(path);
                finish.run();
                return;
            }
            EventType type = attributes == null ? EventType.DELETE
                    : work.type() == EventType.DELETE ? EventType.MODIFY : work.type();
            // Reconcile a committed removal intent with the latest filesystem truth
            // before claiming a new generation or changing imported domain records.
            IndexedFile prior = registerDiscoveredWork(path, attributes);
            if (prior != null
                && (attributes == null && prior.getIndexerStatus() == IndexedFile.IndexerStatus.REMOVED
                    || attributes != null && prior.getIndexerStatus() == IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS
                       && isFileUnchanged(prior, attributes))) {
                finish.run();
                return;
            }
            handleFile(path, attributes, type, finish);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            if (!running || Thread.currentThread().isInterrupted()) {
                log.info("File processing stopped for index={} file={}; leaving work for restart", index, path.getFileName());
            } else {
                log.error("File processing failed for index={} file={}: type={} message={}", index, path.getFileName(),
                        exception.getClass().getSimpleName(), exception.getMessage(), exception);
                markFileAsFailed(path, exception);
            }
            finish.run();
        }
    }

    private void releasePath(Path path) {
        synchronized (monitorFor(path)) {
            processingLocks.remove(path);
        }
        if (running && queuedWork.containsKey(path)) {
            try {
                workerPool.submit(() -> processPendingWork(path));
            } catch (RejectedExecutionException exception) {
                log.warn("Deferred file work stopped for index={} file={}: {}", index, path.getFileName(), exception.toString());
            }
        }
    }

    IndexedFile registerDiscoveredWork(Path file, BasicFileAttributes attributes) {
        AtomicReference<IndexedFile> discovered = new AtomicReference<>();
        executeForPath(file, () -> {
            String name = file.getFileName().toString();
            String relativePath = rel(watchDirectory, file.getParent());
            Optional<IndexedFile> existing = repo.findByIndexAndNameAndPath(index, name, relativePath);
            IndexedFile entity = existing.orElseGet(() -> createNewIndexedFile(file, name, relativePath));
            if (applyDiscoveryState(entity, existing.isPresent(), file, attributes)) repo.saveAndFlush(entity);
            discovered.set(entity);
        });
        return discovered.get();
    }

    private boolean applyDiscoveryState(IndexedFile entity, boolean existing, Path file, BasicFileAttributes attributes) {
        if (attributes != null && !attributes.isRegularFile()) {
            if (existing) {
                boolean active = processingLocks.contains(file);
                entity.setIndexerStatus(active ? IndexedFile.IndexerStatus.RESCHEDULED : IndexedFile.IndexerStatus.FAILED);
                entity.setLastMessage(active ? "New revision requested during active processing" : NON_REGULAR_SOURCE_MESSAGE);
                entity.setIndexUpdateDate(new Date());
                log.warn("Non-regular source retained without import or cleanup: index={} file={}", index, file.getFileName());
            }
            return existing;
        }
        IndexedFile.Operation operation = attributes == null ? IndexedFile.Operation.DELETE : IndexedFile.Operation.UPSERT;
        boolean unchanged = attributes != null && isFileUnchanged(entity, attributes);
        if (canCancelUnstartedRemoval(entity, attributes)) {
            entity.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
            entity.setPendingOperation(IndexedFile.Operation.UPSERT);
            entity.setLastMessage("Unchanged source restored before removal started");
            entity.setIndexUpdateDate(new Date());
            return true;
        }
        if (existing && entity.getIndexerStatus() != IndexedFile.IndexerStatus.RESCHEDULED
            && (unchanged && entity.getIndexerStatus() == IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS
                || attributes == null && entity.getIndexerStatus() == IndexedFile.IndexerStatus.REMOVED)) return false;
        if (existing && processingLocks.contains(file)) {
            if (!unchanged || entity.getPendingOperation() != operation) {
                entity.setIndexerStatus(IndexedFile.IndexerStatus.RESCHEDULED);
                entity.setLastMessage("New revision requested during active processing");
                entity.setIndexUpdateDate(new Date());
                return true;
            }
            return false;
        }
        if (existing && unchanged && entity.getIndexerStatus() == IndexedFile.IndexerStatus.SCHEDULED
            && entity.getPendingOperation() == operation) return false;
        // Capture the successful origin lazily for existing completed rows.
        // A changed discovery no longer represents that imported revision.
        if (entity.getIndexerStatus() == IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS) {
            entity.setLastSuccessfulGeneration(attributes == null || unchanged ? entity.getWorkGeneration() : null);
        } else if (attributes != null && !unchanged) {
            entity.setLastSuccessfulGeneration(null);
        }
        entity.setBasePath(watchDirectory.toString());
        entity.setFullPath(file.toString());
        entity.setIndexUpdateDate(new Date());
        if (attributes != null) {
            entity.setLastModifiedDate(new Date(attributes.lastModifiedTime().toMillis()));
            entity.setSize(attributes.size());
        }
        boolean excluded = attributes != null && (!isIncluded(file) || isExcluded(file));
        entity.setIndexerStatus(excluded ? IndexedFile.IndexerStatus.EXCLUDED : IndexedFile.IndexerStatus.SCHEDULED);
        entity.setPendingOperation(excluded ? null : operation);
        entity.setLastMessage(excluded ? "File excluded by pattern"
                : operation == IndexedFile.Operation.DELETE ? "Removal scheduled" : "Discovered; import pending");
        return true;
    }

    private void markNonRegularSource(Path path) {
        executeForPath(path, () -> repo.findByIndexAndNameAndPath(index, path.getFileName().toString(), rel(watchDirectory, path.getParent()))
                .ifPresent(file -> {
                    file.setIndexerStatus(IndexedFile.IndexerStatus.FAILED);
                    file.setLastMessage(NON_REGULAR_SOURCE_MESSAGE);
                    file.setIndexUpdateDate(new Date());
                    repo.saveAndFlush(file);
                }));
        log.warn("Non-regular source retained without import or cleanup: index={} file={}", index, path.getFileName());
    }

    private boolean canCancelUnstartedRemoval(IndexedFile file, BasicFileAttributes attributes) {
        return attributes != null
               && file.getIndexerStatus() == IndexedFile.IndexerStatus.SCHEDULED
               && file.getPendingOperation() == IndexedFile.Operation.DELETE
               && file.getLastSuccessfulGeneration() != null
               && file.getLastSuccessfulGeneration() == file.getWorkGeneration()
               && isFileUnchanged(file, attributes);
    }

    private void handleFile(Path file, BasicFileAttributes attributes, EventType type, Runnable finish) {
        AtomicReference<IndexedFile> scheduled = new AtomicReference<>();
        AtomicReference<EventType> observerType = new AtomicReference<>();
        if (type == EventType.DELETE || isIncluded(file) && !isExcluded(file)) workStartedListener.run();
        executeForPath(file, () -> {
            String name = file.getFileName().toString();
            String relativePath = rel(watchDirectory, file.getParent());
            Optional<IndexedFile> existing = repo.findByIndexAndNameAndPath(index, name, relativePath);
            IndexedFile entity = existing.orElseGet(() -> createNewIndexedFile(file, name, relativePath));
            observerType.set(effectiveObserverEventType(type, existing.orElse(null)));
            entity.setBasePath(watchDirectory.toString());
            entity.setFullPath(file.toString());
            entity.setIndexUpdateDate(new Date());
            if (attributes != null) {
                entity.setLastModifiedDate(new Date(attributes.lastModifiedTime().toMillis()));
                entity.setSize(attributes.size());
            }
            if (type != EventType.DELETE && (!isIncluded(file) || isExcluded(file))) {
                entity.setIndexerStatus(IndexedFile.IndexerStatus.EXCLUDED);
                entity.setLastMessage("File excluded by pattern");
            } else {
                entity.setWorkGeneration(entity.getWorkGeneration() + 1);
                entity.setPendingOperation(type == EventType.DELETE ? IndexedFile.Operation.DELETE : IndexedFile.Operation.UPSERT);
                entity.setIndexerStatus(IndexedFile.IndexerStatus.SCHEDULED);
                entity.setLastMessage(type == EventType.DELETE ? "Removal scheduled" : "Import scheduled");
            }
            repo.saveAndFlush(entity);
            scheduled.set(entity);
        });
        IndexedFile entity = scheduled.get();
        if (entity.getIndexerStatus() != IndexedFile.IndexerStatus.SCHEDULED) {
            finish.run();
            return;
        }
        // Execute outside the scheduling transaction, after it has fully committed.
        // Ownership remains held until the observer's completion is persisted.
        notifyObserver(entity, observerType.get(), finish);
    }

    static EventType effectiveObserverEventType(EventType eventType, IndexedFile existingFile) {
        if (eventType == EventType.CREATE && existingFile != null
            && !(existingFile.getIndexerStatus() == IndexedFile.IndexerStatus.SCHEDULED
                 && existingFile.getWorkGeneration() == 0 && existingFile.getIndexerInvocations() == 0)) {
            // Atomic-save tools replace a path with a new inode. WatchService reports that as
            // CREATE even though the indexed path already owns domain rows. Treat it as a
            // change so the consumer removes any remaining rows before importing the replacement.
            // This is also safe for a successfully removed file, where cleanup is a no-op.
            return EventType.MODIFY;
        }
        return eventType;
    }

    private void notifyObserver(IndexedFile entity, EventType type, Runnable finish) {
        if (shuttingDown || observer == null || entity.getId() == null) {
            finish.run();
            return;
        }
        long fileId = entity.getId();
        long generation = entity.getWorkGeneration();
        AtomicBoolean completionSignalled = new AtomicBoolean();
        OnCompletion completion = new OnCompletion() {
            @Override
            public void started(long id) {
                requireMatchingId(id, fileId);
                if (completionSignalled.get()) return;
                updateFile(entity, file -> {
                    requireGeneration(file, generation);
                    // A newer discovery may already have queued another revision.
                    if (file.getIndexerStatus() != IndexedFile.IndexerStatus.RESCHEDULED) {
                        file.setIndexerStatus(IndexedFile.IndexerStatus.PROCESSING);
                        file.setLastMessage("Processing started (" + type + ")");
                    }
                });
            }

            @Override
            public void success(long id) {
                complete(id, CompletionOutcome.SUCCESS, null);
            }

            @Override
            public void failed(long id, String reason) {
                complete(id, CompletionOutcome.FAILURE, reason == null || reason.isBlank() ? "Processing failed (" + type + ")" : reason);
            }

            @Override
            public void sourceFailed(long id, String reason) {
                complete(id, CompletionOutcome.SOURCE_FAILURE, reason);
            }

            @Override
            public void skipped(long id, String reason) {
                complete(id, CompletionOutcome.SKIPPED, reason);
            }

            @Override
            public void sourceChanged(long id, String reason) {
                complete(id, CompletionOutcome.SOURCE_CHANGED, reason);
            }

            private void complete(long id, CompletionOutcome outcome, String reason) {
                requireMatchingId(id, fileId);
                if (!completionSignalled.compareAndSet(false, true)) return;
                persistCompletion(entity, type, generation, outcome, reason, finish, 0);
            }
        };
        try {
            if (type == EventType.DELETE) observer.onDeletedFile(index, fileId, completion);
            else if (type == EventType.CREATE) observer.onNewFile(index, fileId, completion);
            else observer.onChangedFile(index, fileId, completion);
        } catch (SupersededWorkException exception) {
            log.info("Superseded callback skipped for index={} fileId={} generation={}", index, fileId, generation);
            finish.run();
        } catch (Exception exception) {
            log.warn("Observer failed for index={} fileId={} type={}: {}", index, fileId, type, exception.toString(), exception);
            completion.failed(fileId, exception.getMessage());
        }
    }

    private void persistCompletion(IndexedFile entity, EventType type, long generation, CompletionOutcome outcome,
                                   String reason, Runnable finish, int attempt) {
        boolean replayRequested;
        try {
            replayRequested = markCompletion(entity, generation, type, outcome, reason);
        } catch (SupersededWorkException exception) {
            log.info("Superseded completion ignored for index={} fileId={} generation={}", index, entity.getId(), generation);
            finish.run();
            return;
        } catch (Exception exception) {
            log.warn("Completion persistence failed for index={} fileId={} generation={} attempt={}: type={} message={}",
                    index, entity.getId(), generation, attempt + 1, exception.getClass().getSimpleName(), exception.getMessage());
            log.debug("Completion persistence failure", exception);
            if (shuttingDown || attempt + 1 >= COMPLETION_PERSISTENCE_RETRY_ATTEMPTS) {
                log.error("Completion left for reconciliation: index={} fileId={} generation={}", index, entity.getId(), generation);
                finish.run();
                return;
            }
            long delay = Math.min(COMPLETION_PERSISTENCE_RETRY_MAX_MS, COMPLETION_PERSISTENCE_RETRY_BASE_MS * (1L << attempt));
            try {
                scheduler.schedule(() -> {
                    try {
                        workerPool.submit(() -> persistCompletion(entity, type, generation, outcome, reason, finish, attempt + 1));
                    } catch (RejectedExecutionException rejected) {
                        log.warn("Completion retry stopped for index={} fileId={}: {}", index, entity.getId(), rejected.toString());
                        finish.run();
                    }
                }, delay, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException rejected) {
                log.warn("Completion retry scheduling stopped for index={} fileId={}: {}", index, entity.getId(), rejected.toString());
                finish.run();
            }
            return;
        }
        try {
            if (replayRequested && running) {
                Path path = safePath(entity);
                if (path != null && !queuedWork.containsKey(normalize(path))) onFsEvent(path, EventType.MODIFY);
            }
        } catch (RejectedExecutionException exception) {
            log.warn("Source replay stopped for index={} fileId={}: {}; committed work remains scheduled", index, entity.getId(), exception.toString());
        } finally {
            finish.run();
        }
    }

    private static void requireMatchingId(long supplied, long expected) {
        if (supplied != expected) throw new IllegalArgumentException("Completion belongs to a different indexed file");
    }

    private static void requireGeneration(IndexedFile file, long expected) {
        if (file.getWorkGeneration() != expected) throw new SupersededWorkException();
    }

    private static final class SupersededWorkException extends RuntimeException {
    }

    private static void completeInitialWork(CountDownLatch latchOrNull, AtomicInteger pendingOrNull) {
        if (latchOrNull == null) {
            return;
        }
        latchOrNull.countDown();
        if (pendingOrNull != null) {
            pendingOrNull.decrementAndGet();
        }
    }

    // ---------- helpers

    private void registerAll(Path start) throws Exception {
        try (var paths = Files.walk(start)) {
            paths.takeWhile(p -> running && !Thread.currentThread().isInterrupted())
                    .filter(Files::isDirectory)
                    .forEach(p -> {
                        try {
                            normalize(p).register(watchService,
                                    StandardWatchEventKinds.ENTRY_CREATE,
                                    StandardWatchEventKinds.ENTRY_MODIFY,
                                    StandardWatchEventKinds.ENTRY_DELETE);
                        } catch (Exception e) {
                            log.warn("Failed to register {}: {}", p, e.getMessage());
                        }
                    });
        }
    }

    private static EventType mapKind(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) return EventType.CREATE;
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) return EventType.MODIFY;
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) return EventType.DELETE;
        return EventType.MODIFY;
    }

    private Path scanPath(IndexedFileScanEntry entry) {
        try {
            Path path = normalize(watchDirectory.resolve(entry.path()).resolve(entry.name()));
            if (!path.startsWith(watchDirectory) || path.equals(watchDirectory)) {
                throw new IllegalArgumentException("Source identity is outside the configured root");
            }
            return path;
        } catch (RuntimeException exception) {
            log.warn("Invalid source identity during recovery: index={} fileId={} type={} message={}",
                    index, entry.id(), exception.getClass().getSimpleName(), exception.getMessage());
            repo.findById(entry.id()).ifPresent(file -> markFailed(file, "Recovery: invalid source path"));
            return null;
        }
    }

    /**
     * Returns true if the file should be considered unchanged according to the
     * configured {@link ChangeDetectionStrategy}.  Only compares size when
     * SIZE_ONLY is active; compares both size and mtime for SIZE_AND_MTIME.
     */
    private boolean isFileUnchanged(IndexedFile prior, BasicFileAttributes attrs) {
        return prior != null && isFileUnchanged(prior.getSize(), prior.getLastModifiedDate(), attrs);
    }

    private boolean isFileUnchanged(Long size, Date modified, BasicFileAttributes attrs) {
        if (size == null) return false;
        boolean sizeMatch = size == attrs.size();
        if (changeDetectionStrategy == ChangeDetectionStrategy.SIZE_ONLY) return sizeMatch;
        return sizeMatch && modified != null && modified.getTime() == attrs.lastModifiedTime().toMillis();
    }

    private static String rel(Path base, Path dir) {
        if (dir == null) return "";
        try {
            return base.relativize(dir.toAbsolutePath().normalize()).toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static Path normalize(Path p) {
        return p.toAbsolutePath().normalize();
    }

    private Path safePath(IndexedFile f) {
        try {
            if (f.getFullPath() != null && !f.getFullPath().isBlank()) return normalize(Paths.get(f.getFullPath()));
            if (f.getBasePath() == null || f.getName() == null) return null;
            if (f.getPath() == null || f.getPath().isBlank()) return normalize(Paths.get(f.getBasePath(), f.getName()));
            return normalize(Paths.get(f.getBasePath(), f.getPath(), f.getName()));
        } catch (Exception e) {
            return null;
        }
    }

    private IndexedFile createNewIndexedFile(Path path, String fileName, String relativePath) {
        Path np = normalize(path);
        IndexedFile nf = new IndexedFile();
        nf.setIndex(index);
        nf.setName(fileName);
        nf.setPath(relativePath);
        nf.setBasePath(watchDirectory.toString());
        nf.setFullPath(np.toString());
        nf.setCreateDate(new Date());
        nf.setIndexAddedDate(new Date());
        nf.setIndexerInvocations(0);
        return nf;
    }

    private void executeRequiresNew(Runnable r) {
        if (txRequiresNew != null) txRequiresNew.execute(s -> {
            r.run();
            return null;
        });
        else r.run();
    }

    private Object monitorFor(Path path) {
        return pathMonitors[Math.floorMod(Objects.hashCode(path), pathMonitors.length)];
    }

    private void executeForPath(Path path, Runnable mutation) {
        int attempt = 0;
        while (true) {
            try {
                synchronized (monitorFor(path)) {
                    executeRequiresNew(mutation);
                }
                return;
            } catch (RuntimeException exception) {
                if (!isConcurrentWorkStateFailure(exception) || ++attempt >= COMPLETION_MAX_RETRIES) throw exception;
                log.warn("Retrying concurrent work state update: index={} path={} attempt={} type={} message={}",
                        index, path, attempt, exception.getClass().getSimpleName(), exception.getMessage());
                log.debug("Concurrent work state update failure for index={} path={}", index, path, exception);
                try {
                    Thread.sleep(COMPLETION_RETRY_BASE_DELAY_MS * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Work state persistence interrupted", interrupted);
                }
            }
        }
    }

    private boolean isConcurrentWorkStateFailure(Throwable exception) {
        if (isOptimisticLockingException(exception)) return true;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && (UNIQUE_VIOLATION_SQL_STATE.equals(sql.getSQLState())
                    || DEADLOCK_SQL_STATE.equals(sql.getSQLState())
                    || SERIALIZATION_FAILURE_SQL_STATE.equals(sql.getSQLState()))) return true;
        }
        return false;
    }

    private boolean isPersistenceFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessException || cause instanceof TransactionException || cause instanceof PersistenceException) return true;
        }
        return false;
    }

    private void updateFile(IndexedFile identity, Consumer<IndexedFile> update) {
        executeForPath(safePath(identity), () -> {
            IndexedFile file = repo.findById(identity.getId())
                    .orElseThrow(() -> new IllegalStateException("IndexedFile not found: " + identity.getId()));
            update.accept(file);
            file.setIndexUpdateDate(new Date());
            repo.save(file);
        });
    }

    private void markFailed(IndexedFile f, String msg) {
        updateFile(f, file -> {
            file.setIndexerStatus(IndexedFile.IndexerStatus.FAILED);
            file.setLastMessage(msg);
        });
    }

    private void markFileAsFailed(Path filePath, Exception exception) {
        try {
            Path np = normalize(filePath);
            String fileName = np.getFileName().toString();
            Path parent = np.getParent();
            String relativePath = parent != null ? rel(watchDirectory, parent) : "";

            executeForPath(np, () -> {
                Optional<IndexedFile> optionalFile = repo.findByIndexAndNameAndPath(index, fileName, relativePath);
                IndexedFile indexedFile = optionalFile.orElseGet(() -> createNewIndexedFile(np, fileName, relativePath));

                // Audit: capture previous status for logging
                IndexedFile.IndexerStatus previousStatus = indexedFile.getIndexerStatus();

                boolean rescheduled = previousStatus == IndexedFile.IndexerStatus.RESCHEDULED;
                indexedFile.setIndexerStatus(rescheduled ? IndexedFile.IndexerStatus.SCHEDULED : IndexedFile.IndexerStatus.FAILED);
                indexedFile.setLastMessage(rescheduled ? "New revision scheduled after previous attempt failed" : "Failed: " + exception.getMessage());
                indexedFile.setIndexUpdateDate(new Date());
                repo.save(indexedFile);

                if (!rescheduled && exception instanceof IOException) {
                    indexedFile.setSourceFailure(true);
                    repo.save(indexedFile);
                    observer.onSourceUnavailable(index, indexedFile.getId(), "The source file could not be read. Check its location and access permissions.");
                }

                // Audit log with all relevant details
                log.warn("AUDIT: File marked as FAILED | index={} | file={} | path={} | previousStatus={} | reason={} | exceptionType={}",
                        index, fileName, relativePath, previousStatus, exception.getMessage(), exception.getClass().getSimpleName());
            });

        } catch (Exception e) {
            log.error("AUDIT: Failed to mark file as FAILED in database | index={} | file={} | originalError={} | dbError={}",
                    index, filePath, exception.getMessage(), e.getMessage(), e);
        }
    }

    private boolean markCompletion(IndexedFile identity, long generation, EventType type, CompletionOutcome outcome, String reason) {
        AtomicBoolean replay = new AtomicBoolean();
        updateFile(identity, file -> {
            requireGeneration(file, generation);
            if (outcome == CompletionOutcome.SUCCESS && type != EventType.DELETE) {
                file.setLastSuccessfulGeneration(generation);
            }
            replay.set(file.getIndexerStatus() == IndexedFile.IndexerStatus.RESCHEDULED || outcome == CompletionOutcome.SOURCE_CHANGED);
            file.setSourceFailure(outcome == CompletionOutcome.SOURCE_FAILURE);
            file.setIndexerStatus(replay.get() ? IndexedFile.IndexerStatus.SCHEDULED : switch (outcome) {
                case SUCCESS -> type == EventType.DELETE ? IndexedFile.IndexerStatus.REMOVED : IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS;
                case FAILURE, SOURCE_FAILURE -> IndexedFile.IndexerStatus.FAILED;
                case SKIPPED -> IndexedFile.IndexerStatus.SKIPPED;
                case SOURCE_CHANGED -> IndexedFile.IndexerStatus.SCHEDULED;
            });
            file.setLastMessage(replay.get() ? "New source revision scheduled" : outcome == CompletionOutcome.SUCCESS
                    ? type == EventType.DELETE ? "Removal completed successfully" : "Completed successfully"
                    : outcome == CompletionOutcome.FAILURE && type == EventType.DELETE ? "Database cleanup failed: " + reason : reason);
            file.setIndexerInvocations(file.getIndexerInvocations() + 1);
        });
        return replay.get();
    }

    private boolean isOptimisticLockingException(Throwable t) {
        while (t != null) {
            String cn = t.getClass().getSimpleName();
            if (cn.contains("Optimistic") || cn.contains("StaleObject")) return true;
            t = t.getCause();
        }
        return false;
    }
}
