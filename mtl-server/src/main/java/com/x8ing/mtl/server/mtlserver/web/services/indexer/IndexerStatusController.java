package com.x8ing.mtl.server.mtlserver.web.services.indexer;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.FileIndexerImpl;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.web.security.DemoWriteAccess;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/indexer")
@RequiredArgsConstructor
@JsonPropertyOrder({
        "indexerStatusService",
        "gpxDirectoryWatcherService",
        "mediaIndexerService",
        "demoWriteAccess"
})
public class IndexerStatusController {

    private static final int DEFAULT_FAILURE_PAGE_SIZE = 25;
    private static final int MAX_FAILURE_PAGE_SIZE = 100;

    private final IndexerStatusService indexerStatusService;

    private final GPXDirectoryWatcherService gpxDirectoryWatcherService;

    private final MediaIndexerService mediaIndexerService;
    private final DemoWriteAccess demoWriteAccess;

    @Operation(operationId = "getIndexerStatus")
    @GetMapping("/status")
    public List<IndexerStatusService.IndexSummaryDto> getStatus() {
        return indexerStatusService.getIndexSummaries();
    }

    @Operation(operationId = "getIndexerFileFailures")
    @GetMapping("/{index}/failures")
    public IndexerStatusService.FileFailuresDto getFileFailures(
            @PathVariable String index,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_FAILURE_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "FAILED") com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile.IndexerStatus outcome
    ) {
        if (page < 0 || size < 1 || size > MAX_FAILURE_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid failure page or size");
        }
        if (outcome != com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile.IndexerStatus.FAILED
            && outcome != com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile.IndexerStatus.SKIPPED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose failed or skipped files.");
        return indexerStatusService.getFileOutcomes(index.trim().toUpperCase(Locale.ROOT), page, size, outcome);
    }

    @Operation(operationId = "triggerIndexerRescan")
    @PostMapping("/{index}/rescan")
    public IndexerRescanResponse triggerRescan(@PathVariable String index) {
        demoWriteAccess.requireImportAndReviewAccess();
        String normalizedIndex = index.trim().toUpperCase(Locale.ROOT);
        FileIndexerImpl.RescanRequestStatus status = switch (normalizedIndex) {
            case GPXDirectoryWatcherService.INDEX_GPS -> gpxDirectoryWatcherService.requestRescan();
            case MediaIndexerService.INDEX_MEDIA -> mediaIndexerService.requestRescan();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported index: " + index);
        };
        return new IndexerRescanResponse(
                normalizedIndex,
                IndexerRescanStatus.valueOf(status.name()),
                messageFor(normalizedIndex, status)
        );
    }

    private static String messageFor(String index, FileIndexerImpl.RescanRequestStatus status) {
        return switch (status) {
            case STARTED -> "Manual " + index + " rescan has been queued.";
            case ALREADY_RUNNING -> index + " scan is already running.";
            case NOT_RUNNING -> index + " indexer is not ready yet.";
        };
    }
}
