package com.x8ing.mtl.server.mtlserver.jobs.demo;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * Periodic self-correcting job (demo mode only) that ensures exactly
 * {@code mtl.demo-target-track-count} "good" tracks are visible.
 *
 * <p>Runs every 60 s and converges on the target regardless of when the activity-type
 * detection or other background jobs mark tracks as suspicious.
 * <ol>
 *   <li>Exclude suspicious tracks (bulk SQL)</li>
 *   <li>If good-track count &gt; target → trim excess</li>
 *   <li>If good-track count &lt; target → re-enable non-suspicious tracks that were previously trimmed</li>
 * </ol>
 *
 * <p>All operations are bulk SQL — no entities are loaded into memory.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DemoTrackExclusionService {

    private static final Date SUSPICIOUS_DATE_CUTOFF =
            Date.from(LocalDate.of(1971, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC));

    private final MtlAppProperties appProperties;
    private final GpsTrackRepository gpsTrackRepository;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void reconcileDemoTracks() {
        if (!appProperties.isDemoMode()) {
            return;
        }

        // ── Step 1: Exclude suspicious tracks ──
        int suspiciousExcluded = gpsTrackRepository.excludeSuspiciousTracks(SUSPICIOUS_DATE_CUTOFF);
        if (suspiciousExcluded > 0) {
            log.info("[DEMO] Marked {} suspicious track(s) as EXCLUDED.", suspiciousExcluded);
        }

        // ── Step 2: Adjust to target count ──
        int targetCount = appProperties.getDemoTargetTrackCount();
        if (targetCount <= 0) {
            return;  // no target set — keep all good tracks
        }

        long goodCount = gpsTrackRepository.countGoodTracks();

        if (goodCount > targetCount) {
            int trimmed = gpsTrackRepository.excludeGoodTracksExceedingOffset(targetCount);
            log.info("[DEMO] Trimmed: {} good → target {} (excluded {} excess).",
                    goodCount, targetCount, trimmed);
        } else if (goodCount < targetCount) {
            // Re-enable non-suspicious tracks that were previously excluded for count trimming
            int deficit = (int) (targetCount - goodCount);
            int restored = gpsTrackRepository.reEnableNonSuspiciousExcludedTracks(SUSPICIOUS_DATE_CUTOFF, deficit);
            if (restored > 0) {
                log.info("[DEMO] Restored {} previously trimmed tracks (had {}, target {}).",
                        restored, goodCount, targetCount);
            }
        }
    }
}
