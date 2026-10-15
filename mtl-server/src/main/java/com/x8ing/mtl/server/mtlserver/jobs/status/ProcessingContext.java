package com.x8ing.mtl.server.mtlserver.jobs.status;

import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import com.x8ing.mtl.server.mtlserver.jobs.ResourceIntensiveJobGuard;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Request-local facts are memoized; their meaning belongs to each job. */
public final class ProcessingContext {
    private final IndexerStatusService indexers;
    private final BackgroundJobRunner runner;
    private final ResourceIntensiveJobGuard guard;
    private final Map<String, Boolean> busyIndexes = new HashMap<>();
    private final boolean demoMode;
    private Boolean demoBusy;

    public ProcessingContext(IndexerStatusService indexers, BackgroundJobRunner runner,
                             ResourceIntensiveJobGuard guard, boolean demoMode) {
        this.indexers = indexers;
        this.runner = runner;
        this.guard = guard;
        this.demoMode = demoMode;
    }

    public static ProcessingContext indexes(IndexerStatusService indexers) {
        return new ProcessingContext(indexers, null, null, false);
    }

    public boolean indexBusy(String id) { return busyIndexes.computeIfAbsent(id, indexers::hasIndexPendingWork); }
    public boolean demoMode() { return demoMode; }
    public boolean demoIndexingBusy() {
        if (demoBusy == null) demoBusy = indexers.isDemoIndexingBusy();
        return demoBusy;
    }
    public boolean executing(String job) { return runner.isExecuting(job); }
    public Instant nextRunAt(String job) { return runner.nextRunAt(job); }

    public List<ProcessingDetails.Blocker> resourceBlockers(ProcessingJob job, long ready) {
        String owner = guard.heldBy();
        if (!job.resourceIntensive() || ready == 0 || owner == null || owner.equals(job.jobId())) return List.of();
        return List.of(new ProcessingDetails.Blocker(ProcessingDetails.Kind.LOCK, ProcessingDetails.RESOURCE_LOCK,
                "RESOURCE_BUSY", ProcessingDetails.Scope.WHOLE_JOB, ProcessingDetails.Resolution.AUTOMATIC, owner));
    }


}
