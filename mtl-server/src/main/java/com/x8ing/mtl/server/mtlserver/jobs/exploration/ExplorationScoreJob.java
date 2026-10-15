package com.x8ing.mtl.server.mtlserver.jobs.exploration;

import com.x8ing.mtl.server.mtlserver.jobs.ImportPrerequisites;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.config.ConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import com.x8ing.mtl.server.mtlserver.jobs.status.JobStatusService.JobSummaryDto;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingJob;
import com.x8ing.mtl.server.mtlserver.utils.ThreadFactories;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@JsonPropertyOrder({
        "gpsTrackRepository",
        "atomicWorker",
        "configRepository",
        "indexerStatusService",
        "transactionTemplate",
        "corridorWidthM",
        "useTrackPrecision",
        "maxTracksPerRun",
        "workerThreads"
})
public class ExplorationScoreJob implements ProcessingJob {

    // Config table keys for storing the last-used algorithm parameters.
    // If these values change between runs, all CALCULATED tracks are reset for recalculation.
    private static final String CONFIG_DOMAIN1 = "exploration";
    private static final String CONFIG_DOMAIN2 = "algo";
    private static final String CONFIG_DOMAIN3_CORRIDOR = "corridorWidthM";
    private static final String CONFIG_DOMAIN3_PRECISION = "useTrackPrecision";
    private static final int MIN_WORKER_THREADS = 1;
    private static final String EXPLORATION_THREAD_PREFIX = "explore";

    private final GpsTrackRepository gpsTrackRepository;
    private final ExplorationScoreAtomicWorker atomicWorker;
    private final ConfigRepository configRepository;
    private final IndexerStatusService indexerStatusService;
    private final TransactionTemplate transactionTemplate;
    private final ShutdownSignal shutdownSignal;

    /**
     * Radius in meters defining "known territory" around prior tracks.
     * Changing this value invalidates all previously computed scores (detected via config table snapshot).
     *
     * @see ExplorationScoreAtomicWorker#corridorWidthM
     */
    @Value("${mtl.exploration.corridor-width-m:25.0}")
    private double corridorWidthM;

    /**
     * Simplified track precision level in meters (maps to gps_track_data.precision_in_meter).
     * Changing this value invalidates all previously computed scores.
     *
     * @see ExplorationScoreAtomicWorker#useTrackPrecision
     */
    @Value("${mtl.exploration.use-track-precision:10}")
    private int useTrackPrecision;

    /**
     * Maximum number of tracks to process in a single job run. Prevents the scheduler thread
     * from retaining the whole backlog. Full batches that make progress yield through BackgroundJobRunner
     * before continuing without the idle polling interval.
     */
    @Value("${mtl.exploration.max-tracks-per-run:20}")
    private int maxTracksPerRun;

    /**
     * Number of tracks to process concurrently within a fetched batch. Scores depend on
     * prior track geometry by start_date, not on prior exploration_score values, so a batch
     * can be calculated in parallel without changing the result.
     */
    @Value("${mtl.exploration.worker-threads:3}")
    private int workerThreads;

    public ExplorationScoreJob(GpsTrackRepository gpsTrackRepository,
                               ExplorationScoreAtomicWorker atomicWorker,
                               ConfigRepository configRepository,
                               IndexerStatusService indexerStatusService,
                               TransactionTemplate transactionTemplate,
                               ShutdownSignal shutdownSignal) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.atomicWorker = atomicWorker;
        this.configRepository = configRepository;
        this.indexerStatusService = indexerStatusService;
        this.transactionTemplate = transactionTemplate;
        this.shutdownSignal = shutdownSignal;
    }

    /**
     * Main entry called by the scheduler. Returns true if a full batch was processed
     * (meaning there may be more work to do immediately).
     * <p>
     * Internally manages its own transaction boundaries:
     * <ol>
     *   <li>Transaction 1: guard check, config change detection, bulk invalidation of stale tracks → commit (releases row locks)</li>
     *   <li>Transaction 2: fetch pending batch and process each track (atomicWorker uses its own REQUIRES_NEW per track)</li>
     * </ol>
     */
    public boolean run() { return reportedBatch().hasMore(); }

    public ProcessingDetails.BatchOutcome reportedBatch() {
        if (shutdownSignal.isStopRequested()) return ProcessingDetails.BatchOutcome.finished(false);
        // Transaction 1: preparation — guard + invalidation, committed before processing starts
        Boolean shouldProceed = transactionTemplate.execute(status -> {
            var blockers = prerequisites(ProcessingContext.indexes(indexerStatusService));
            if (!blockers.isEmpty()) {
                log.info("Exploration score job waiting: blockers={}", blockers);
                return false;
            }

            checkConfigChange();

            int invalidated = gpsTrackRepository.invalidateCalculatedTracksOverlappingScheduled();
            if (invalidated > 0) {
                log.info("Exploration score job: invalidated {} calculated tracks overlapping newly scheduled tracks.", invalidated);
            }
            return true;
        });

        if (!Boolean.TRUE.equals(shouldProceed) || shutdownSignal.isStopRequested()) {
            return ProcessingDetails.BatchOutcome.waiting(prerequisites(ProcessingContext.indexes(indexerStatusService)));
        }

        // Transaction 2: short read-only transaction to fetch the pending batch.
        // Deliberately closed before processing begins so the DB connection is not held
        // idle-in-transaction across the (potentially 100+ second) processing loop.
        // Each track is processed by atomicWorker.processOne with its own REQUIRES_NEW transaction.
        List<GpsTrack> tracks = transactionTemplate.execute(status -> {
            List<GpsTrack> result = gpsTrackRepository.findByExplorationStatusIn(
                    List.of(GpsTrack.EXPLORATION_STATUS.SCHEDULED, GpsTrack.EXPLORATION_STATUS.NEEDS_RECALCULATION),
                    PageRequest.of(0, maxTracksPerRun)
            );
            // Detach all entities so the outer Hibernate session releases them cleanly.
            // processOne will re-read each track inside its own REQUIRES_NEW transaction.
            return result;
        });

        if (tracks == null || tracks.isEmpty() || shutdownSignal.isStopRequested()) {
            log.debug("Exploration score job: no pending tracks.");
            return ProcessingDetails.BatchOutcome.finished(false);
        }

        BackgroundJobRunner.batchStarted();
        int effectiveWorkerThreads = Math.max(MIN_WORKER_THREADS, workerThreads);
        log.info("Exploration score job: processing {} tracks (max={}, workerThreads={})",
                tracks.size(), maxTracksPerRun, effectiveWorkerThreads);

        long t0 = System.currentTimeMillis();
        BatchProgress progress = processTracks(tracks, effectiveWorkerThreads);
        long durationMs = System.currentTimeMillis() - t0;
        log.info("Exploration score job completed: processed={} failed={} remaining={} stopped={} durationMs={}",
                progress.completed(), progress.failed(), tracks.size() - progress.completed() - progress.failed(),
                shutdownSignal.isStopRequested(), durationMs);

        // Committed retries do not block healthy tracks. An escaped worker failure can leave a track
        // SCHEDULED and cause the next batch to invalidate completed tracks again, so stop that chain.
        return ProcessingDetails.BatchOutcome.finished(!shutdownSignal.isStopRequested() && !progress.workerFailed() && progress.completed() > 0
               && tracks.size() >= maxTracksPerRun);
    }

    private BatchProgress processTracks(List<GpsTrack> tracks, int effectiveWorkerThreads) {
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        if (effectiveWorkerThreads == MIN_WORKER_THREADS || tracks.size() == 1) {
            for (GpsTrack track : tracks) {
                if (shutdownSignal.isStopRequested()) break;
                if (atomicWorker.processOne(track.getId())) processed.incrementAndGet();
                else failed.incrementAndGet();
            }
            return new BatchProgress(processed.get(), failed.get(), false);
        }

        boolean workerFailed = false;
        ExecutorService executor = Executors.newFixedThreadPool(effectiveWorkerThreads,
                ThreadFactories.named(EXPLORATION_THREAD_PREFIX));
        try {
            List<Future<?>> futures = new ArrayList<>(tracks.size());
            for (GpsTrack track : tracks) {
                if (shutdownSignal.isStopRequested()) break;
                futures.add(executor.submit(() -> {
                    if (shutdownSignal.isStopRequested()) return;
                    if (atomicWorker.processOne(track.getId())) processed.incrementAndGet();
                    else failed.incrementAndGet();
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Exploration score job interrupted while waiting for workers.");
                    return new BatchProgress(processed.get(), failed.get(), workerFailed);
                } catch (ExecutionException e) {
                    failed.incrementAndGet();
                    workerFailed = true;
                    log.error("Exploration score worker failed outside per-track error handling. e={}",
                            e.getCause() == null ? e.toString() : e.getCause().toString(), e);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return new BatchProgress(processed.get(), failed.get(), workerFailed);
    }

    private record BatchProgress(int completed, int failed, boolean workerFailed) {
    }

    /**
     * Compare current algorithm config against stored snapshot. If changed, reset all
     * CALCULATED tracks to NEEDS_RECALCULATION and update the stored snapshot.
     */
    private void checkConfigChange() {
        String currentCorridorStr = String.valueOf(corridorWidthM);
        String currentPrecisionStr = String.valueOf(useTrackPrecision);

        String storedCorridor = getConfigValue(CONFIG_DOMAIN3_CORRIDOR);
        String storedPrecision = getConfigValue(CONFIG_DOMAIN3_PRECISION);

        boolean changed = false;
        if (storedCorridor == null || !storedCorridor.equals(currentCorridorStr)) {
            changed = true;
        }
        if (storedPrecision == null || !storedPrecision.equals(currentPrecisionStr)) {
            changed = true;
        }

        if (changed) {
            int reset = gpsTrackRepository.resetAllExplorationScores();
            log.info("Exploration config changed (corridor={}→{}, precision={}→{}). Reset {} tracks for recalculation.",
                    storedCorridor, currentCorridorStr, storedPrecision, currentPrecisionStr, reset);
            storeConfigValue(CONFIG_DOMAIN3_CORRIDOR, currentCorridorStr);
            storeConfigValue(CONFIG_DOMAIN3_PRECISION, currentPrecisionStr);
        }
    }

    private String getConfigValue(String domain3) {
        List<ConfigEntity> configs = configRepository.findConfigEntitiesByDomain1AndDomain2AndDomain3(
                CONFIG_DOMAIN1, CONFIG_DOMAIN2, domain3);
        return configs.isEmpty() ? null : configs.getFirst().getValue();
    }

    private void storeConfigValue(String domain3, String value) {
        List<ConfigEntity> configs = configRepository.findConfigEntitiesByDomain1AndDomain2AndDomain3(
                CONFIG_DOMAIN1, CONFIG_DOMAIN2, domain3);
        ConfigEntity config;
        if (configs.isEmpty()) {
            config = new ConfigEntity();
            config.domain1 = CONFIG_DOMAIN1;
            config.domain2 = CONFIG_DOMAIN2;
            config.domain3 = domain3;
        } else {
            config = configs.getFirst();
        }
        config.value = value;
        config.updateDate = new Date();
        configRepository.save(config);
    }

    public static final String JOB_ID = "exploration";
    @Override public String jobId() { return JOB_ID; }
    @Override public int displayOrder() { return 40; }
    @Override public boolean resourceIntensive() { return true; }
    @Override public Duration checkInterval() { return org.springframework.boot.convert.DurationStyle.detectAndParse(runSchedule); }
    @Override public Duration initialDelay() { return Duration.ofSeconds(30); }
    @Value("${mtl.exploration.run-schedule:PT120S}")
    private String runSchedule = "PT120S";
    @Override public List<ProcessingDetails.Blocker> prerequisites(ProcessingContext context) {
        var blockers = new ArrayList<>(ImportPrerequisites.check(context, context.demoMode()));
        if (gpsTrackRepository.existsByTrackSourceAndDuplicateStatus(
                GpsTrack.TRACK_SOURCE.IMPORTED, GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET)) {
            blockers.add(ProcessingDetails.Blocker.automatic(ProcessingDetails.Kind.JOB, "duplicate",
                    "DUPLICATE_CHECKS_PENDING", ProcessingDetails.Scope.WHOLE_JOB));
        }
        return List.copyOf(blockers);
    }
    @Override public List<ProcessingDetails.Dependency> dependencies(ProcessingContext context) {
        var edges = new ArrayList<ProcessingDetails.Dependency>();
        edges.add(new ProcessingDetails.Dependency(ProcessingDetails.Kind.INDEX, "GPS", JOB_ID,
                ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB));
        edges.add(new ProcessingDetails.Dependency(ProcessingDetails.Kind.JOB, "duplicate", JOB_ID,
                ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB));
        if (context.demoMode()) edges.add(new ProcessingDetails.Dependency(ProcessingDetails.Kind.INDEX, "MEDIA", JOB_ID,
                ProcessingDetails.Relation.GATE, ProcessingDetails.Scope.WHOLE_JOB));
        return List.copyOf(edges);
    }
    @Override public JobSummaryDto processingStatus(ProcessingContext context) {
        var counts = new EnumMap<GpsTrack.EXPLORATION_STATUS, Long>(GpsTrack.EXPLORATION_STATUS.class);
        gpsTrackRepository.countGroupedByExplorationStatus().forEach(row ->
                counts.put((GpsTrack.EXPLORATION_STATUS) row[0], (Long) row[1]));
        long pending = counts.getOrDefault(GpsTrack.EXPLORATION_STATUS.SCHEDULED, 0L)
                + counts.getOrDefault(GpsTrack.EXPLORATION_STATUS.IN_PROGRESS, 0L)
                + counts.getOrDefault(GpsTrack.EXPLORATION_STATUS.NEEDS_RECALCULATION, 0L);
        long done = counts.getOrDefault(GpsTrack.EXPLORATION_STATUS.CALCULATED, 0L)
                + gpsTrackRepository.countExplorationExplicitlySkipped();
        var blockers = prerequisites(context);
        return JobSummaryDto.counts(JOB_ID, "Exploration score", pending, done, 0, 0, 0, null)
                .processing(this, context, blockers.isEmpty() ? 0 : pending, 0, blockers, null, "tracks", true);
    }

}
