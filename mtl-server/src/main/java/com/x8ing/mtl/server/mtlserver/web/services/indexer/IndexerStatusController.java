package com.x8ing.mtl.server.mtlserver.web.services.indexer;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.FileIndexerImpl;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
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
        "mediaIndexerService"
})
public class IndexerStatusController {

    private final IndexerStatusService indexerStatusService;

    private final GPXDirectoryWatcherService gpxDirectoryWatcherService;

    private final MediaIndexerService mediaIndexerService;

    @Operation(operationId = "getIndexerStatus")
    @GetMapping("/status")
    public List<IndexerStatusService.IndexSummaryDto> getStatus() {
        return indexerStatusService.getIndexSummaries();
    }

    @Operation(operationId = "triggerIndexerRescan")
    @PostMapping("/{index}/rescan")
    public IndexerRescanResponse triggerRescan(@PathVariable String index) {
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
