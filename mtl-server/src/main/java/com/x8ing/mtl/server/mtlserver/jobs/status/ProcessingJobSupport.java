package com.x8ing.mtl.server.mtlserver.jobs.status;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import com.x8ing.mtl.server.mtlserver.jobs.ResourceIntensiveJobGuard;

import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class ProcessingJobSupport {
    private final IndexerStatusService indexers;
    private final ResourceIntensiveJobGuard guard;
    private final MtlAppProperties properties;

    public ProcessingJobSupport(IndexerStatusService indexers, ResourceIntensiveJobGuard guard, MtlAppProperties properties) {
        this.indexers = indexers;
        this.guard = guard;
        this.properties = properties;
    }

    public ProcessingContext context(BackgroundJobRunner runner) {
        return new ProcessingContext(indexers, runner, guard, properties.isDemoMode());
    }

    public ProcessingDetails.BatchOutcome execute(ProcessingJob job, BackgroundJobRunner runner, Supplier<ProcessingDetails.BatchOutcome> work) {
        if (!job.reportingEnabled()) return ProcessingDetails.BatchOutcome.finished(false);
        var context = context(runner);
        var blockers = job.prerequisites(context);
        if (!blockers.isEmpty()) return ProcessingDetails.BatchOutcome.waiting(blockers);
        if (job.resourceIntensive()) {
            return guard.runReportedBatch(job.jobId(), () -> runner.executing(job.jobId(), work));
        }
        return runner.executing(job.jobId(), work);
    }
}
