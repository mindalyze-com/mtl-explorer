package com.x8ing.mtl.server.mtlserver.web.services.status;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.status.JobStatusService;

import java.util.List;

@JsonPropertyOrder({
        "indexers",
        "jobs", "dependenciesVersion", "dependencies"
})
public record ProcessingStatusDto(
        List<IndexerStatusService.IndexSummaryDto> indexers,
        List<JobStatusService.JobSummaryDto> jobs,
        String dependenciesVersion,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        List<com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails.Dependency> dependencies
) {
    public ProcessingStatusDto(List<IndexerStatusService.IndexSummaryDto> indexers, List<JobStatusService.JobSummaryDto> jobs) {
        this(indexers, jobs, null, null);
    }
}
