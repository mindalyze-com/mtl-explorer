package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.event.FileIndexerObserver;
import com.x8ing.mtl.server.mtlserver.indexer.event.OnCompletion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class FileIndexerImpl {

    // ---- ctor args / collaborators
    private final String index;
    private final Path watchDirectory;
    private final IndexerRepository repo;
    private final FileIndexerObserver observer;
    private final TransactionTemplate txRequiresNew;

    // ---- runtime
    private volatile boolean running = false;
    private WatchService watchService;
    private volatile Thread watcherThread;

    // per-path debounce task (silence-based)
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, namedFactory("debounce"));
    private final ExecutorService workerPool =
            Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()),
                    namedFactory("indexer-worker"));

    // debounce state
    private final Map<Path, ScheduledFuture<?>> debounceTasks = new ConcurrentHashMap<>();
    private final Map<Path, Long> debounceSeq = new ConcurrentHashMap<>();           // sequence token per path
    private final Map<Path, EventType> pendingEventType = new ConcurrentHashMap<>(); // coalesced type per path

    // prevent concurrent process of same path
    private final Set<Path> processingLocks = ConcurrentHashMap.newKeySet();

    // guard against concurrent overflow rescans
    private final AtomicBoolean rescanInProgress = new AtomicBoolean(false);

    // guard for both initial scan and periodic rescans
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);

    // include/exclude
    private final List<PathMatcher> inclusionMatchers = new CopyOnWriteArrayList<>();
    private final List<PathMatcher> exclusionMatchers = new CopyOnWriteArrayList<>();

    // change-detection strategy
    public enum ChangeDetectionStrategy {SIZE_ONLY, SIZE_AND_MTIME}

    private ChangeDetectionStrategy changeDetectionStrategy = ChangeDetectionStrategy.SIZE_ONLY;

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
        this.index = index;
        this.watchDirectory = watchDirectory.toAbsolutePath().normalize();
        this.repo = repo;
        this.observer = observer;

        TransactionTemplate t = null;
        if (txManager != null) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            t = new TransactionTemplate(txManager, def);
        }
        this.txRequiresNew = t;
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

    public boolean isScanInProgress() {
        return scanInProgress.get();
    }

    /**
     * Trigger a full rescan of the watch directory against the DB.
     * Silently skips if a scan is already running. Safe to call from a scheduler.
     */
    public void rescan() {
        if (!running) {
            log.warn("rescan() called but indexer is not running for index={}", index);
            return;
        }
        if (!scanInProgress.compareAndSet(false, true)) {
            log.info("Periodic rescan skipped — scan already in progress for index={}", index);
            return;
        }
        workerPool.submit(() -> {
            try {
                log.info("Periodic rescan starting for index={}", index);
                performInitialScanAndRecovery();
                log.info("Periodic rescan completed for index={}", index);
            } catch (Exception e) {
                log.error("Periodic rescan failed for index={}", index, e);
            } finally {
                scanInProgress.set(false);
            }
        });
    }

    private boolean isIncluded(Path p) {
        if (inclusionMatchers.isEmpty()) return true;
        Path abs = p.toAbsolutePath().normalize();
        Path name = abs.getFileName();
        for (PathMatcher m : inclusionMatchers) {
            try {
                if (m.matches(abs) || (name != null && m.matches(name))) return true;
            } catch (Exception ignore) {
            }
        }
        return false;
    }

    private boolean isExcluded(Path p) {
        if (exclusionMatchers.isEmpty()) return false;
        Path abs = p.toAbsolutePath().normalize();
        Path name = abs.getFileName();
        for (PathMatcher m : exclusionMatchers) {
            try {
                if (m.matches(abs) || (name != null && m.matches(name))) return true;
            } catch (Exception ignore) {
            }
        }
        return false;
    }

    // ---------- lifecycle

    public void start(boolean blocking) {
        if (running) {
            log.warn("Already running for index={}", index);
            return;
        }
        if (!Files.isDirectory(watchDirectory)) {
            throw new IllegalStateException("watchDirectory must exist: " + watchDirectory);
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
        } else {
            // Non-blocking: entire boot runs on a daemon thread
            Thread boot = new Thread(this::bootInternal, "indexer-boot-" + index);
            boot.setDaemon(true);
            boot.start();
        }
    }

    private void bootInternal() {
        scanInProgress.set(true);
        try {
            running = true;
            // Initial batch (scan + recovery + latch await)
            performInitialScanAndRecovery();
            // Live watcher (may be disabled for indexes with very large directory trees)
            if (liveWatchEnabled) {
                startWatcher();
            } else {
                log.info("Live file watching disabled for index={} - relying on periodic rescans only", index);
            }
        } catch (Throwable t) {
            log.error("Boot sequence failed for index={}", index, t);
            running = false;
        } finally {
            scanInProgress.set(false);
        }
    }

    private void startWatcher() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerAll(watchDirectory);
            log.info("Live watcher started at {}", watchDirectory);
        } catch (Exception e) {
            log.error("WatchService init failed", e);
            running = false;
            return;
        }

        watcherThread = new Thread(this::watchLoopSafe, "watcher-" + index);
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    public void shutdown() {
        running = false;

        // Cancel all pending debounce tasks
        debounceTasks.values().forEach(f -> f.cancel(false));
        debounceTasks.clear();
        debounceSeq.clear();
        pendingEventType.clear();

        try {
            if (watchService != null) watchService.close();
        } catch (Exception ignore) {
        }

        scheduler.shutdown();
        workerPool.shutdown();

        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("Stopped indexer {}", index);
    }

    // ---------- initial batch (two-phase + latch + recovery)

    private record FileToProcess(Path path, BasicFileAttributes attrs, EventType type) {
    }

    private record DirScanResult(List<FileToProcess> changed, List<IndexedFile> missing) {
    }

    private enum EventType {CREATE, MODIFY, DELETE}

    /**
     * Walk the watch directory and compare every file against the DB snapshot (size + mtime).
     * Returns changed/new files and DB rows whose files no longer exist on disk.
     */
    private DirScanResult scanDirectoryAgainstDb() {
        Map<String, IndexedFile> dbSnapshot = preloadExisting();
        List<FileToProcess> changed = new ArrayList<>();
        AtomicInteger examined = new AtomicInteger(0);

        log.info("Directory walk starting for index={} path={}", index, watchDirectory);
        try {
            Files.walk(watchDirectory)
                    .filter(Files::isRegularFile)
                    .filter(this::isIncluded)
                    .filter(p -> !isExcluded(p))
                    .forEach(p -> {
                        try {
                            Path np = normalize(p);
                            BasicFileAttributes a = Files.readAttributes(np, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                            String key = keyOf(rel(watchDirectory, np.getParent()), np.getFileName().toString());
                            IndexedFile prior = dbSnapshot.remove(key);
                            if (prior != null && prior.getSize() != null
                                && prior.getIndexerStatus() != IndexedFile.IndexerStatus.REMOVED) {
                                // REMOVED files are always re-queued regardless of size/mtime match:
                                // a false REMOVED (transient FS hiccup) must be corrected on rescan.
                                if (isFileUnchanged(prior, a)) {
                                    int n = examined.incrementAndGet();
                                    if (n % 1000 == 0)
                                        log.info("Walk progress index={}: {} files examined, {} queued for processing", index, n, changed.size());
                                    return;
                                }
                            }
                            // If prior was REMOVED (found on disk → false removal) treat as MODIFY so
                            // processCreateOrChange(changed=true) will delete stale data before re-importing.
                            EventType eventType = (prior == null) ? EventType.CREATE : EventType.MODIFY;
                            changed.add(new FileToProcess(np, a, eventType));
                            int n = examined.incrementAndGet();
                            if (n % 1000 == 0) log.info("Walk progress index={}: {} files examined, {} queued for processing", index, n, changed.size());
                        } catch (IOException e) {
                            log.warn("Attrs read failed: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.error("File walk failed", e);
        }
        log.info("Directory walk completed for index={}: {} files examined, {} queued for processing, {} missing from disk",
                index, examined.get(), changed.size(), dbSnapshot.size());

        return new DirScanResult(changed, new ArrayList<>(dbSnapshot.values()));
    }

    // ---------- initial batch (two-phase + latch + recovery)
    private void performInitialScanAndRecovery() {
        long start = System.currentTimeMillis();
        log.info("Starting initial scan of {}", watchDirectory);

        // STEP 0: Very simple reclaim — PROCESSING -> SCHEDULED, using existing repo methods only
        try {
            List<IndexedFile> processing = repo.findByIndexAndIndexerStatus(index, IndexedFile.IndexerStatus.PROCESSING);
            if (!processing.isEmpty()) {
                Date now = new Date();
                for (IndexedFile f : processing) {
                    f.setIndexerStatus(IndexedFile.IndexerStatus.SCHEDULED);
                    f.setLastMessage("Restart: re-queued from PROCESSING");
                    f.setIndexUpdateDate(now);
                    // do NOT alter name/path/basePath/etc.
                    repo.save(f); // existing JPA method
                }
                log.info("Reclaimed {} PROCESSING -> SCHEDULED for index={}", processing.size(), index);
            }
        } catch (Exception e) {
            log.warn("PROCESSING reclaim failed for index={}: {}", index, e.toString());
        }

        // Phase A: walk disk and collect new/changed vs DB snapshot
        DirScanResult scan = scanDirectoryAgainstDb();
        List<FileToProcess> phaseA = scan.changed();
        List<IndexedFile> missing = scan.missing();

        // Phase B: recovery of leftovers (SCHEDULED only — PROCESSING already flipped above)
        List<FileToProcess> recoveredModify = new ArrayList<>();
        List<IndexedFile> recoveredDelete = new ArrayList<>();
        int recoveredExcluded = 0;

        Set<String> plannedKeys = new HashSet<>();
        for (FileToProcess f : phaseA) {
            plannedKeys.add(keyOf(rel(watchDirectory, f.path().getParent()), f.path().getFileName().toString()));
        }

        try {
            List<IndexedFile> leftovers = repo.findByIndexAndIndexerStatus(index, IndexedFile.IndexerStatus.SCHEDULED);
            if (!leftovers.isEmpty()) {
                for (IndexedFile lf : leftovers) {
                    String k = keyOf(lf.getPath(), lf.getName());
                    if (plannedKeys.contains(k)) continue; // already covered by Phase A

                    Path p = safePath(lf);
                    if (p == null) {
                        markFailed(lf, "Recovery: missing path info");
                        continue;
                    }
                    Path np = normalize(p);

                    if (!Files.exists(np)) {
                        recoveredDelete.add(lf);
                        continue;
                    }
                    if (!isIncluded(np) || isExcluded(np)) {
                        executeRequiresNew(() -> {
                            lf.setIndexerStatus(IndexedFile.IndexerStatus.EXCLUDED);
                            lf.setLastMessage("Excluded on restart recovery");
                            lf.setIndexUpdateDate(new Date());
                            repo.save(lf);
                        });
                        recoveredExcluded++;
                        continue;
                    }

                    try {
                        BasicFileAttributes a = Files.readAttributes(np, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        // Enqueue as MODIFY so observer gets onChangedFile(...)
                        recoveredModify.add(new FileToProcess(np, a, EventType.MODIFY));
                    } catch (IOException io) {
                        markFailed(lf, "Recovery stat failed: " + io.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Recovery query failed", e);
        }

        log.info("Initial scan: index={} new/changed={} missing={} recoveredChanged={} recoveredDeleted={} recoveredExcluded={}",
                index, phaseA.size(), missing.size(), recoveredModify.size(), recoveredDelete.size(), recoveredExcluded);

        // Latch & dispatch
        int total = phaseA.size() + missing.size() + recoveredModify.size() + recoveredDelete.size();
        AtomicInteger pending = new AtomicInteger(total);
        CountDownLatch latch = new CountDownLatch(total);

        for (FileToProcess f : phaseA) workerPool.submit(() -> dispatchWithLatch(f, latch, pending));
        for (FileToProcess f : recoveredModify) workerPool.submit(() -> dispatchWithLatch(f, latch, pending));
        for (IndexedFile m : missing) workerPool.submit(() -> dispatchDeleteWithLatch(m, latch, pending));
        for (IndexedFile m : recoveredDelete) workerPool.submit(() -> dispatchDeleteWithLatch(m, latch, pending));

        log.info("Awaiting {} initial callbacks...", total);
        try {
            boolean ok = latch.await(48, TimeUnit.HOURS);
            if (!ok) {
                log.warn("Initial scan timeout; {} still pending", pending.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Initial scan wait interrupted; {} still pending", pending.get());
        }

        long took = System.currentTimeMillis() - start;
        double seconds = took / 1000.0;
        double filesPerSecond = seconds > 0 ? total / seconds : 0;
        log.info("Initial scan fully completed for index={} in {} ms ({} files, {} files/sec)",
                index, took, total, String.format("%.1f", filesPerSecond));
    }


    private void dispatchWithLatch(FileToProcess f, CountDownLatch latch, AtomicInteger pending) {
        try {
            processFileOnce(f.path(), f.attrs(), f.type(), latch, pending);
        } catch (Exception e) {
            log.warn("Initial dispatch failed for {}: {}", f.path(), e.getMessage(), e);
            latch.countDown();
            pending.decrementAndGet();
        }
    }

    private void dispatchDeleteWithLatch(IndexedFile row, CountDownLatch latch, AtomicInteger pending) {
        try {
            handleMissingFileOnStartup(row, latch, pending);
        } catch (Exception e) {
            log.warn("Initial delete dispatch failed for {}: {}", row.getFullPath(), e.getMessage(), e);
            latch.countDown();
            pending.decrementAndGet();
        }
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
                if (evt.kind() == StandardWatchEventKinds.OVERFLOW) {
                    log.warn("WatchService OVERFLOW for index={} — events may have been lost, scheduling rescan", index);
                    workerPool.submit(this::rescanAfterOverflow);
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

        // Last-write-wins for event type; filesystem truth is checked at processing time
        pendingEventType.put(k, type);

        // Cancel previous timer and schedule a new one with a fresh sequence token
        ScheduledFuture<?> prev = debounceTasks.get(k);
        if (prev != null) {
            prev.cancel(false);
        } else {
            log.info("Live watcher detected {} for: {}", type, k.getFileName());
        }

        long token = debounceSeq.merge(k, 1L, Long::sum);

        Runnable trigger = () -> {
            // stale-task guard: skip if a newer event already superseded this timer
            Long current = debounceSeq.get(k);
            if (current == null || current != token) return;

            workerPool.submit(() -> processDebounced(k));
        };

        ScheduledFuture<?> fut = scheduler.schedule(trigger, DEBOUNCE_SILENCE.toMillis(), TimeUnit.MILLISECONDS);
        debounceTasks.put(k, fut);
    }

    /**
     * Loop-on-release pattern: process all pending events for this path,
     * continuing to drain any that arrive during processing (e.g., during
     * waitUntilStable). This prevents events from being silently dropped
     * when a file is modified while already being processed.
     */
    private void processDebounced(Path k) {
        if (!processingLocks.add(k)) {
            // Another worker is already processing this path.
            // That worker's loop will pick up our pending event before releasing the lock.
            log.debug("Already processing {}, active worker will pick up pending event", k.getFileName());
            return;
        }

        try {
            while (true) {
                EventType eventType = pendingEventType.remove(k);
                if (eventType == null) break;

                try {
                    // Filesystem is the source of truth for existence (fixes DELETE+CREATE coalescing)
                    BasicFileAttributes attrs = null;
                    boolean fileExists = Files.exists(k);

                    if (fileExists) {
                        attrs = waitUntilStable(k);
                        if (attrs == null) {
                            // File was deleted during stability check
                            log.info("File {} disappeared during stability check; treating as DELETE", k.getFileName());
                            eventType = EventType.DELETE;
                        } else if (eventType == EventType.DELETE) {
                            log.info("File {} coalesced as DELETE but exists on disk; treating as MODIFY", k.getFileName());
                            eventType = EventType.MODIFY;
                        }
                    } else {
                        eventType = EventType.DELETE;
                    }

                    log.debug("Debounce settled for {} | type={} | attrs={} | size={} | lastModified={}",
                            k.getFileName(), eventType, attrs != null,
                            (attrs != null ? attrs.size() : null),
                            (attrs != null ? attrs.lastModifiedTime() : null));

                    // For MODIFY events, check if file actually changed per changeDetectionStrategy.
                    // This prevents "fake" reprocessing when only mtime changed (e.g. NAS restart)
                    // but content (size) is identical and strategy is SIZE_ONLY.
                    if (eventType == EventType.MODIFY && attrs != null) {
                        String fileName = k.getFileName().toString();
                        String relativePath = (k.getParent() != null) ? rel(watchDirectory, k.getParent()) : "";
                        Optional<IndexedFile> opt = repo.findByIndexAndNameAndPath(index, fileName, relativePath);
                        if (opt.isPresent()) {
                            IndexedFile prior = opt.get();
                            // REMOVED/FAILED files are always re-processed (recovery)
                            if (prior.getIndexerStatus() != IndexedFile.IndexerStatus.REMOVED
                                && prior.getIndexerStatus() != IndexedFile.IndexerStatus.FAILED
                                && isFileUnchanged(prior, attrs)) {
                                log.debug("Live watch: skipping unchanged file {} (strategy={})",
                                        k.getFileName(), changeDetectionStrategy);
                                continue;
                            }
                        }
                    }

                    handleFile(k, attrs, eventType, null, null);

                } catch (Exception e) {
                    log.error("Live process failed for {}: {} - Marking as FAILED in database",
                            k.getFileName(), e.getMessage(), e);
                    markFileAsFailed(k, e);
                }
            }
        } finally {
            processingLocks.remove(k);
            // Check for events that arrived between our last remove() and lock release
            if (pendingEventType.containsKey(k)) {
                workerPool.submit(() -> processDebounced(k));
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
            log.warn("Starting overflow rescan for index={}", index);

            // Re-register all directories (new subdirs may have been missed during overflow)
            registerAll(watchDirectory);

            DirScanResult scan = scanDirectoryAgainstDb();

            for (FileToProcess f : scan.changed()) {
                onFsEvent(f.path(), f.type());
            }
            for (IndexedFile m : scan.missing()) {
                Path p = safePath(m);
                if (p != null) {
                    onFsEvent(p, EventType.DELETE);
                }
            }

            log.info("Overflow rescan completed for index={}: changed={} missing={}",
                    index, scan.changed().size(), scan.missing().size());
        } catch (Exception e) {
            log.error("Overflow rescan failed for index={}", index, e);
        } finally {
            rescanInProgress.set(false);
        }
    }

    private BasicFileAttributes waitUntilStable(Path p) throws Exception {
        if (!Files.exists(p)) return null;
        BasicFileAttributes a1 = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!a1.isRegularFile()) return a1;

        int attempts = 0;
        while (attempts <= STABILITY_RETRY_ATTEMPTS) {
            boolean stable = true;
            BasicFileAttributes prev = a1;
            for (int i = 0; i < REQUIRED_STABLE_PROBES; i++) {
                Thread.sleep(PROBE_INTERVAL.toMillis());
                if (!Files.exists(p)) return null; // file deleted during stability check
                BasicFileAttributes a2 = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
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

    // ---------- core processing (single place)

    private void processFileOnce(Path file, BasicFileAttributes attrs, EventType type,
                                 CountDownLatch latchOrNull, AtomicInteger pendingOrNull) {
        Path k = normalize(file);
        if (!processingLocks.add(k)) {
            log.info("Already processing {}, skipping", k.getFileName());
            if (latchOrNull != null) {
                latchOrNull.countDown();
                if (pendingOrNull != null) pendingOrNull.decrementAndGet();
            }
            return;
        }
        try {
            handleFile(k, attrs, type, latchOrNull, pendingOrNull);
        } finally {
            processingLocks.remove(k);
        }
    }

    private void handleFile(Path file, BasicFileAttributes attrs, EventType type,
                            CountDownLatch latchOrNull, AtomicInteger pendingOrNull) {

        String fileName = file.getFileName().toString();
        String relativePath = (file.getParent() != null) ? rel(watchDirectory, file.getParent()) : "";

        // TRACE: Confirm we reached handleFile
        log.debug("handleFile({}, type={}, attrsPresent={}, relativePath={})",
                fileName, type, attrs != null, relativePath);

        Runnable scheduleWork = () -> {
            Optional<IndexedFile> opt = repo.findByIndexAndNameAndPath(index, fileName, relativePath);
            IndexedFile entity = opt.orElseGet(() -> createNewIndexedFile(file, fileName, relativePath));

            entity.setIndexUpdateDate(new Date());
            if (attrs != null) {
                entity.setLastModifiedDate(new Date(attrs.lastModifiedTime().toMillis()));
                entity.setSize(attrs.size());
            } else {
                entity.setSize(null);
            }

            boolean isDelete = (type == EventType.DELETE);
            if (!isDelete && attrs == null) {
                // Only confirm existence via Files.exists when we don't already have fresh attrs.
                // attrs non-null means the caller just read file attributes (Phase A walk, Phase B
                // recovery, or processDebounced waitUntilStable), confirming the file existed
                // moments ago.  A second Files.exists call here races with transient FS unavailability
                // (Docker/NFS volumes) and can falsely mark a live file as REMOVED.
                try {
                    if (!Files.exists(file)) isDelete = true;
                } catch (Exception ignore) {
                }
            }

            if (isDelete) {
                entity.setIndexerStatus(IndexedFile.IndexerStatus.REMOVED);
                entity.setLastMessage("File removed");
            } else if (!isIncluded(file) || isExcluded(file)) {
                entity.setIndexerStatus(IndexedFile.IndexerStatus.EXCLUDED);
                entity.setLastMessage("File excluded by pattern");
                log.info("File {} excluded by pattern (included={}, excluded={})",
                        fileName, isIncluded(file), isExcluded(file));
            } else {
                entity.setIndexerStatus(IndexedFile.IndexerStatus.SCHEDULED);
                entity.setLastMessage(type == EventType.CREATE ? "New file scheduled" : "Changed file scheduled");
            }
            repo.saveAndFlush(entity);

            boolean shouldNotify = (entity.getIndexerStatus() == IndexedFile.IndexerStatus.SCHEDULED ||
                                    entity.getIndexerStatus() == IndexedFile.IndexerStatus.REMOVED);
            if (latchOrNull != null) {
                latchOrNull.countDown();
                if (pendingOrNull != null) pendingOrNull.decrementAndGet();
            }
            if (shouldNotify && entity.getId() != null) {
                // Schedule notification AFTER transaction commits
                registerAfterCommit(() -> notifyObserver(entity, type));
            }
        };

        executeRequiresNew(scheduleWork);
    }

    private void notifyObserver(IndexedFile entity, EventType type) {
        if (observer == null || entity.getId() == null) return;
        final long fileId = entity.getId();

        // Completion routes back into the indexer
        OnCompletion completion = new OnCompletion() {
            @Override
            public void started(long fid) {
                // Flip SCHEDULED → PROCESSING only when the consumer actually begins work
                try {
                    markProcessing(fid, "Processing started (" + type + ")");
                } catch (Exception e) {
                    log.error("Completion started() failed for fileId={}: {}", fid, e.toString(), e);
                }
            }

            @Override
            public void success(long fid) {
                try {
                    markSuccess(fid);
                } catch (Exception e) {
                    log.error("Completion success failed for fileId={}: {}", fid, e.toString(), e);
                }
            }

            @Override
            public void failed(long fid, String reason) {
                try {
                    markFailure(fid, (reason == null || reason.isBlank())
                            ? "Consumer failure (" + type + ")"
                            : reason);
                } catch (Exception e) {
                    log.error("Completion failed() failed for fileId={}: {}", fid, e.toString(), e);
                }
            }
        };

        try {
            if (type == EventType.DELETE || entity.getIndexerStatus() == IndexedFile.IndexerStatus.REMOVED) {
                observer.onDeletedFile(index, fileId, completion);
            } else if (type == EventType.CREATE) {
                observer.onNewFile(index, fileId, completion);
            } else {
                observer.onChangedFile(index, fileId, completion);
            }
        } catch (Exception e) {
            String reason = (e.getMessage() == null || e.getMessage().isBlank())
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            log.warn("Observer threw for fileId={} type={}: {}", fileId, type, reason, e);
            completion.failed(fileId, "Observer error: " + reason);
        }
    }

    private void markProcessing(long fileId, String msg) {
        executeRequiresNew(() -> {
            IndexedFile f = repo.findById(fileId)
                    .orElseThrow(() -> new IllegalStateException("IndexedFile not found: " + fileId));
            if (f.getIndexerStatus() != IndexedFile.IndexerStatus.REMOVED
                && f.getIndexerStatus() != IndexedFile.IndexerStatus.EXCLUDED) {
                f.setIndexerStatus(IndexedFile.IndexerStatus.PROCESSING);
            }
            f.setLastMessage(msg);
            f.setIndexUpdateDate(new Date());
            repo.save(f);
        });
    }

    private void handleMissingFileOnStartup(IndexedFile missing, CountDownLatch latch, AtomicInteger pending) {
        executeRequiresNew(() -> {
            missing.setIndexerStatus(IndexedFile.IndexerStatus.REMOVED);
            missing.setLastMessage("File removed (detected on startup)");
            missing.setIndexUpdateDate(new Date());
            repo.save(missing);
        });
        notifyObserver(missing, EventType.DELETE);
        if (latch != null) {
            latch.countDown();
            if (pending != null) pending.decrementAndGet();
        }
    }

    // ---------- helpers

    private void registerAll(Path start) throws Exception {
        Files.walk(start)
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

    private static EventType mapKind(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) return EventType.CREATE;
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) return EventType.MODIFY;
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) return EventType.DELETE;
        return EventType.MODIFY;
    }

    private Map<String, IndexedFile> preloadExisting() {
        Map<String, IndexedFile> map = new ConcurrentHashMap<>();
        try {
            List<IndexedFile> rows = repo.findByIndex(index);
            for (IndexedFile f : rows) {
                // Include REMOVED entries so scanDirectoryAgainstDb can detect files that were
                // incorrectly marked REMOVED (e.g. due to transient FS unavailability) and
                // re-queue them as MODIFY rather than treating them as brand-new CREATEs.
                // EXCLUDED files are still skipped — they should remain excluded.
                if (f.getIndexerStatus() != IndexedFile.IndexerStatus.EXCLUDED) {
                    map.put(keyOf(f.getPath(), f.getName()), f);
                }
            }
            log.info("Preloaded {} existing entries", map.size());
        } catch (Exception e) {
            log.warn("Preload failed: {}", e.getMessage());
        }
        return map;
    }

    /**
     * Returns true if the file should be considered unchanged according to the
     * configured {@link ChangeDetectionStrategy}.  Only compares size when
     * SIZE_ONLY is active; compares both size and mtime for SIZE_AND_MTIME.
     */
    private boolean isFileUnchanged(IndexedFile prior, BasicFileAttributes attrs) {
        if (prior == null || prior.getSize() == null) return false;
        boolean sizeMatch = prior.getSize().equals(attrs.size());
        if (changeDetectionStrategy == ChangeDetectionStrategy.SIZE_ONLY) {
            return sizeMatch;
        }
        boolean mtimeMatch = prior.getLastModifiedDate() != null
                             && prior.getLastModifiedDate().getTime() == attrs.lastModifiedTime().toMillis();
        return sizeMatch && mtimeMatch;
    }

    private static String keyOf(String rel, String name) {
        return (rel == null ? "" : rel) + "|" + (name == null ? "" : name);
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

    private void markFailed(IndexedFile f, String msg) {
        executeRequiresNew(() -> {
            f.setIndexerStatus(IndexedFile.IndexerStatus.FAILED);
            f.setLastMessage(msg);
            f.setIndexUpdateDate(new Date());
            repo.save(f);
        });
    }

    private void markFileAsFailed(Path filePath, Exception exception) {
        try {
            Path np = normalize(filePath);
            String fileName = np.getFileName().toString();
            Path parent = np.getParent();
            String relativePath = parent != null ? rel(watchDirectory, parent) : "";

            executeRequiresNew(() -> {
                Optional<IndexedFile> optionalFile = repo.findByIndexAndNameAndPath(index, fileName, relativePath);
                IndexedFile indexedFile = optionalFile.orElseGet(() -> createNewIndexedFile(np, fileName, relativePath));

                // Audit: capture previous status for logging
                IndexedFile.IndexerStatus previousStatus = indexedFile.getIndexerStatus();

                indexedFile.setIndexerStatus(IndexedFile.IndexerStatus.FAILED);
                indexedFile.setLastMessage("Failed: " + exception.getMessage());
                indexedFile.setIndexUpdateDate(new Date());
                repo.save(indexedFile);

                // Audit log with all relevant details
                log.warn("AUDIT: File marked as FAILED | index={} | file={} | path={} | previousStatus={} | reason={} | exceptionType={}",
                        index, fileName, relativePath, previousStatus, exception.getMessage(), exception.getClass().getSimpleName());
            });

        } catch (Exception e) {
            log.error("AUDIT: Failed to mark file as FAILED in database | index={} | file={} | originalError={} | dbError={}",
                    index, filePath, exception.getMessage(), e.getMessage(), e);
        }
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(0);
        return r -> {
            Thread t = new Thread(r);
            t.setName(prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    private void registerAfterCommit(Runnable callback) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    workerPool.submit(callback);
                }
            });
        } else {
            // No active transaction (shouldn't happen with txRequiresNew, but fallback)
            log.warn("No active transaction synchronization - executing callback immediately");
            workerPool.submit(callback);
        }
    }

    // ---- completion API (public) ----
    public void markSuccess(long fileId) {
        markCompletion(fileId, true, null);
    }

    public void markFailure(long fileId, String reason) {
        markCompletion(fileId, false, reason);
    }

    private void markCompletion(long fileId, boolean success, String reason) {
        final int MAX_RETRIES = 3;
        int attempt = 0;
        while (true) {
            try {
                executeRequiresNew(() -> {
                    IndexedFile f = repo.findById(fileId).orElseThrow(() -> new IllegalStateException("IndexedFile not found: " + fileId));
                    if (success) {
                        if (f.getIndexerStatus() != IndexedFile.IndexerStatus.REMOVED && f.getIndexerStatus() != IndexedFile.IndexerStatus.EXCLUDED) {
                            f.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
                            f.setLastMessage("Completed successfully");
                        }
                    } else {
                        if (f.getIndexerStatus() != IndexedFile.IndexerStatus.REMOVED && f.getIndexerStatus() != IndexedFile.IndexerStatus.EXCLUDED) {
                            f.setIndexerStatus(IndexedFile.IndexerStatus.FAILED);
                            f.setLastMessage(reason != null ? reason : "Processing failed");
                        }
                    }
                    f.setIndexerInvocations(f.getIndexerInvocations() + 1);
                    f.setIndexUpdateDate(new Date());
                    repo.save(f);
                });
                return; // success
            } catch (Exception e) {
                attempt++;
                if (!isOptimisticLockingException(e) || attempt >= MAX_RETRIES) {
                    log.warn("Completion update failed (attempt {}/{}) for fileId={} success={} reason={} e={}", attempt, MAX_RETRIES, fileId, success, reason, e.toString());
                    return;
                }
                try {
                    Thread.sleep(25L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
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
