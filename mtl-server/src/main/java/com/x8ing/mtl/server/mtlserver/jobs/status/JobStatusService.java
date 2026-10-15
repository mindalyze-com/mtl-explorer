package com.x8ing.mtl.server.mtlserver.jobs.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@JsonPropertyOrder({
        "jobs", "support", "runner"
})
public class JobStatusService {

    private final List<ProcessingJob> jobs;
    private final ProcessingJobSupport support;
    private final BackgroundJobRunner runner;

    public JobStatusService(List<ProcessingJob> jobs, ProcessingJobSupport support,
                            BackgroundJobRunner runner) {
        this.jobs = jobs.stream().sorted(Comparator.comparingInt(ProcessingJob::displayOrder)).toList();
        this.support = support;
        this.runner = runner;
    }

    @JsonPropertyOrder({
            "job",
            "label",
            "total",
            "pending",
            "done",
            "failed",
            "unavailable",
            "attentionRequired",
            "unavailableReasons",
            "progressPercent", "state", "work", "blockedBy", "nextRunAt", "nextRetryAt", "unit", "progressKnown"
    })
    public record JobSummaryDto(
            String job,
            String label,
            long total,
            long pending,
            long done,
            long failed,
            long unavailable,
            long attentionRequired,
            @JsonInclude(JsonInclude.Include.NON_NULL) UnavailableReasonsDto unavailableReasons,
            int progressPercent,
            ProcessingDetails.State state,
            ProcessingDetails.Work work,
            List<ProcessingDetails.Blocker> blockedBy,
            Instant nextRunAt,
            Instant nextRetryAt,
            String unit,
            boolean progressKnown
    ) {
        public JobSummaryDto(String job, String label, long total, long pending, long done, long failed,
                             long unavailable, long attentionRequired, UnavailableReasonsDto reasons, int progress) {
            this(job, label, total, pending, done, failed, unavailable, attentionRequired, reasons, progress,
                    null, null, List.of(), null, null, "tracks", true);
        }

        public static JobSummaryDto counts(String job, String label, long pending, long done, long failed,
                                           long unavailable, long attention, UnavailableReasonsDto reasons) {
            long finished = done + failed + unavailable;
            long total = pending + finished;
            return new JobSummaryDto(job, label, total, pending, done, failed, unavailable, attention, reasons,
                    total == 0 ? 100 : (int) (finished * 100L / total));
        }

        public JobSummaryDto processing(ProcessingJob owner, ProcessingContext context, long waiting, long retrying,
                                         List<ProcessingDetails.Blocker> blockers, Instant retryAt,
                                         String unit, boolean known) {
            long safeWaiting = Math.min(pending, Math.max(0, waiting));
            long safeRetrying = Math.min(pending - safeWaiting, Math.max(0, retrying));
            var work = new ProcessingDetails.Work(pending - safeWaiting - safeRetrying, safeWaiting, safeRetrying);
            var currentBlockers = new ArrayList<ProcessingDetails.Blocker>();
            currentBlockers.addAll(blockers);
            currentBlockers.addAll(context.resourceBlockers(owner, work.ready()));
            boolean active = context.executing(job);
            var state = ProcessingDetails.state(owner.reportingEnabled(), active, work);
            boolean wholeJobWait = blockers.stream().anyMatch(blocker ->
                    blocker.scope() == ProcessingDetails.Scope.WHOLE_JOB && blocker.resolution() == ProcessingDetails.Resolution.AUTOMATIC);
            if (state == ProcessingDetails.State.COMPLETE && wholeJobWait) {
                state = ProcessingDetails.State.WAITING;
            }
            return new JobSummaryDto(job, label, total, pending, done, failed, unavailable, attentionRequired,
                    unavailableReasons, progressPercent, state, work, List.copyOf(currentBlockers),
                    state == ProcessingDetails.State.COMPLETE ? null : context.nextRunAt(job), retryAt, unit,
                    known && !(wholeJobWait && pending == 0));
        }

        public JobSummaryDto(String job, String label, long total, long pending, long done, long failed, int progressPercent) {
            this(job, label, total, pending, done, failed, 0, failed, null, progressPercent);
        }
    }

    @JsonPropertyOrder({"noDataPoints", "unsupportedTiming", "missingActivity", "missingCalculation"})
    public record UnavailableReasonsDto(long noDataPoints, long unsupportedTiming,
                                       long missingActivity, long missingCalculation) {
    }

    public List<JobSummaryDto> getJobSummaries() {
        var context = support.context(runner);
        return jobs.stream().filter(ProcessingJob::reportingEnabled).map(job -> job.processingStatus(context)).toList();
    }

    public List<ProcessingDetails.Dependency> getDependencies() {
        var context = support.context(runner);
        return jobs.stream().filter(ProcessingJob::reportingEnabled)
                .flatMap(job -> job.dependencies(context).stream()).distinct().toList();
    }
    public String dependenciesVersion(List<ProcessingDetails.Dependency> dependencies) {
        // Configuration is stable for this server lifetime. A restart/configuration change invalidates client metadata.
        return Integer.toHexString(dependencies.toString().hashCode());
    }

}
