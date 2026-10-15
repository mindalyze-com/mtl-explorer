package com.x8ing.mtl.server.mtlserver.jobs.status;

import java.time.Duration;
import java.util.List;

/** Domain-owned reporting contract. The collector does not interpret prerequisites. */
public interface ProcessingJob {
    String jobId();
    int displayOrder();
    JobStatusService.JobSummaryDto processingStatus(ProcessingContext context);
    List<ProcessingDetails.Blocker> prerequisites(ProcessingContext context);
    List<ProcessingDetails.Dependency> dependencies(ProcessingContext context);
    default boolean reportingEnabled() { return true; }
    default boolean resourceIntensive() { return false; }
    default Duration checkInterval() { return Duration.ofSeconds(20); }
    default Duration initialDelay() { return Duration.ofSeconds(5); }
}
