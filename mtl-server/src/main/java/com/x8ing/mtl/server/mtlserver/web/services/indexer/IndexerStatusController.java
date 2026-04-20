package com.x8ing.mtl.server.mtlserver.web.services.indexer;

import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/indexer")
@RequiredArgsConstructor
public class IndexerStatusController {

    private final IndexerStatusService indexerStatusService;

    @Operation(operationId = "getIndexerStatus")
    @GetMapping("/status")
    public List<IndexerStatusService.IndexSummaryDto> getStatus() {
        return indexerStatusService.getIndexSummaries();
    }
}
