package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/segment-attempts")
@RequiredArgsConstructor
public class SegmentAnalysisController {
    private final SegmentCoreService core;
    private final SegmentHealthService health;
    private final SegmentComparisonService comparison;
    private final SegmentTrackHeartRateService tableHeartRate;

    @PostMapping("/heart-rate-summaries")
    @Operation(operationId = "getSegmentAttemptHeartRateSummaries", summary = "Get per-attempt and covered-time consolidated HR for one track")
    public ResponseEntity<SegmentTrackHeartRate> getSegmentAttemptHeartRateSummaries(@RequestBody java.util.List<String> references) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tableHeartRate.get(references));
    }

    @GetMapping("/{reference}")
    @Operation(operationId = "getSegmentAttemptAnalysis", summary = "Get authoritative metrics and geometry for one exact segment attempt")
    public ResponseEntity<SegmentCoreAnalysis> getSegmentAttemptAnalysis(@PathVariable String reference,
                                                                         @RequestParam(required = false) Integer maxBuckets,
                                                                         @RequestParam(name = "x", defaultValue = "TIME") XMode axis) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(core.get(reference, maxBuckets, axis));
    }

    @GetMapping("/{reference}/health")
    @Operation(operationId = "getSegmentAttemptHealth", summary = "Get exact-interval HR and cardiac cost for a segment attempt")
    public ResponseEntity<SegmentHealthAnalysis> getSegmentAttemptHealth(@PathVariable String reference,
                                                                         @RequestParam(required = false) Integer maxBuckets) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(health.get(reference, maxBuckets));
    }

    @GetMapping("/{reference}/comparison")
    @Operation(operationId = "getSegmentAttemptComparison", summary = "Compare finish times and elapsed times at equal traveled distance")
    public ResponseEntity<SegmentComparison> getSegmentAttemptComparison(@PathVariable String reference,
                                                                         @RequestParam String referenceAttempt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(comparison.get(reference, referenceAttempt));
    }
}
