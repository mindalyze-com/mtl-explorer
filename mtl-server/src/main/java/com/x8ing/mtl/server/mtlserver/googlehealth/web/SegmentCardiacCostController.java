package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
public class SegmentCardiacCostController {
    private final SegmentCardiacCostService service;

    @Operation(operationId = "getSegmentCardiacCost", summary = "Get cardiac cost over covered moving intervals of a track segment")
    @GetMapping("/{trackId}/segment-cardiac-cost")
    public ResponseEntity<SegmentCardiacCostSummary> getSegmentCardiacCost(
            @PathVariable long trackId, @RequestParam long fromPointId, @RequestParam long toPointId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(trackId, fromPointId, toPointId));
    }
}
