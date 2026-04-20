package com.x8ing.mtl.server.mtlserver.web.services.indexer;

import com.x8ing.mtl.server.mtlserver.jobs.status.JobStatusService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobStatusController {

    private final JobStatusService jobStatusService;

    @Operation(operationId = "getJobStatus")
    @GetMapping("/status")
    public List<JobStatusService.JobSummaryDto> getStatus() {
        return jobStatusService.getJobSummaries();
    }
}
