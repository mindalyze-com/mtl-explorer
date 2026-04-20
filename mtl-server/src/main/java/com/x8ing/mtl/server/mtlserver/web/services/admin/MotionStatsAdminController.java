package com.x8ing.mtl.server.mtlserver.web.services.admin;

import com.x8ing.mtl.server.mtlserver.logic.motion.MotionStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin trigger for the one-shot stop-stats backfill. Runs through every
 * already-stored track that has no {@code trackDurationStoppedSecs} yet and
 * recomputes the totals from its {@code RAW_OUTLIER_CLEANED} points.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class MotionStatsAdminController {

    private final MotionStatsService motionStatsService;

    @PostMapping("/motion-stats/backfill")
    public Map<String, Object> backfill() {
        long t0 = System.currentTimeMillis();
        int updated = motionStatsService.backfillMissing();
        long dtMs = System.currentTimeMillis() - t0;
        log.info("Motion-stats backfill complete: updated={}, dtMs={}", updated, dtMs);
        return Map.of(
                "updated", updated,
                "durationMs", dtMs
        );
    }
}
